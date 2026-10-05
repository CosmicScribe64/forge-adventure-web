import org.teavm.gradle.api.OptimizationLevel

plugins {
    java
    id("com.github.xpenatan.gdx-teavm") version "1.6.1"
}

repositories {
    mavenCentral()
    maven("https://teavm.org/maven/repository/")
    maven("https://jitpack.io")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Forge modules and their runtime dependencies, copied by scripts/build-forge-libs.
    // (Forge's installed POMs use an unresolved ${revision}, so Gradle can't read them.)
    implementation(fileTree("libs") { include("*.jar") })
    // FreeType compiled to WebAssembly; Forge renders all its fonts with FreeType.
    implementation("com.github.xpenatan.gdx-teavm:gdx-freetype-web:1.6.1")
    // For our SubstitutionPolicy (maps missing APIs and stubbed classes to web/src).
    compileOnly("org.teavm:teavm-extension-spi:0.15.0")
    // For our compiler plugin (forgeweb.teavm.CallRedirector).
    compileOnly("org.teavm:teavm-core:0.15.0")
    // For our Inflater shim; TeaVM's classlib brings it at runtime.
    compileOnly("com.jcraft:jzlib:1.1.3")
}

val worker = System.getenv("WORKER") == "true"

gdxTeaVM {
    // Preloaded at startup and read with Gdx.files.internal (Forge's fallback skin).
    assets.from(file("webassets"))
    // gdx-teavm reflection exposes every field and method, so it's used only for what needs
    // gdx-teavm's own registry. Everything else Forge reflects on is in forgeweb.teavm.WebReflection
    // (fields or constructors only), because broad patterns here made TeaVM run out of memory.
    reflectionDebug = System.getenv("REFLECTION_DEBUG") == "true"
    // The worker needs none of the reflection metadata below (it would pull those classes in).
    if (!worker) {
        // libGDX Json loads these (its generic-field lookup, FieldGen, requires this registry).
        reflection("forge.adventure.data.**")
        reflection("forge.adventure.world.BiomeSprites*")
        // Named in adventure/common/skin/ui_skin.json.
        reflection("com.ray3k.tenpatch.**")
        // TypingLabel builds {EFFECT} tags through these classes' reflected constructors.
        reflection("com.github.tommyettinger.textra.effects.**")
        // Loaded by name by gdx-controllers (see WebLauncher).
        reflection("com.badlogic.gdx.controllers.ControllerManagerStub")
    }

    js {
        // SELFTEST=true builds forgeweb.selftest.SelfTest instead of the game (scripts/selftest).
        if (System.getenv("SELFTEST") == "true") {
            mainClass = "forgeweb.selftest.SelfTest"
            outputDir = layout.buildDirectory.dir("dist/selftest")
        } else if (worker) {
            // WORKER=true: the world-generation Web Worker (wfc-worker.js, see scripts/build-web).
            mainClass = "forgeweb.worker.WfcWorker"
            outputDir = layout.buildDirectory.dir("dist/worker")
        } else {
            mainClass = "forge.web.WebLauncher"
        }
        // NONE compiles faster but runs far too slowly to load 34k card scripts.
        optimization = OptimizationLevel.valueOf(System.getenv("TEAVM_OPT") ?: "BALANCED")
        // Quicker, less precise whole-program analysis; try TEAVM_FAST_ANALYSIS=true to compare.
        fastGlobalAnalysis = System.getenv("TEAVM_FAST_ANALYSIS") == "true"
        // Minified names. Off everywhere except for SelfTest with TEAVM_OBFUSCATED=true
        // (scripts/selftest), to find code that depends on Java names before the release is
        // minified.
        obfuscated = System.getenv("SELFTEST") == "true" && System.getenv("TEAVM_OBFUSCATED") == "true"
        outOfProcess = true
        // Forge is about 400k lines, and the default heap is far too small. Override with TEAVM_MEMORY_MB.
        processMemory = (System.getenv("TEAVM_MEMORY_MB") ?: "5120").toInt()
    }

    // WebAssembly GC target (task gdx_teavm_web_wasm_build), same entry points as js.
    wasm {
        if (System.getenv("SELFTEST") == "true") {
            mainClass = "forgeweb.selftest.SelfTest"
            outputDir = layout.buildDirectory.dir("dist/selftest-wasm")
        } else {
            mainClass = "forge.web.WebLauncher"
            outputDir = layout.buildDirectory.dir("dist/wasm")
        }
        optimization = OptimizationLevel.valueOf(System.getenv("TEAVM_OPT") ?: "BALANCED")
        obfuscated = false
        outOfProcess = true
        processMemory = (System.getenv("TEAVM_MEMORY_MB") ?: "5120").toInt()
    }
}

// TeaVM 0.15 converts double and float to long with BigInt(Math.floor(x)), so NaN and infinities
// throw ("The number NaN cannot be converted to a BigInt", a crash when resizing the window) and
// large values wrap. Java gives 0 for NaN and clamps to Long.MIN_VALUE and Long.MAX_VALUE. The
// runtime snippet (long.js in teavm-core) is read through TeaVM's own class loader, so it can't be
// shadowed like classlib classes. Instead, the generated app.js is patched. SelfTest checks the
// result.
tasks.matching { it.name == "gdx_teavm_web_js_build" }.configureEach {
    doLast {
        // Matched without the trailing ";" or ",", because TeaVM ends the definition with either,
        // depending on the output.
        val broken = "Long_fromNumber = val => BigInt.asIntN(64, BigInt(val >= 0 ? Math.floor(val) : Math.ceil(val)))"
        val fixed = "Long_fromNumber = val => (val !== val ? BigInt(0) : val >= 9223372036854775807 ? BigInt(\"9223372036854775807\")" +
            " : val <= -9223372036854775808 ? BigInt(\"-9223372036854775808\") : BigInt(val >= 0 ? Math.floor(val) : Math.ceil(val)))"
        // Patches only this build's output (see js { outputDir } above), and streams it, because
        // app.js is about 70 MB.
        val dir = when {
            System.getenv("SELFTEST") == "true" -> "dist/selftest"
            System.getenv("WORKER") == "true" -> "dist/worker"
            else -> "dist/js"
        }
        val app = layout.buildDirectory.file("$dir/webapp/app.js").get().asFile
        val tmp = File(app.path + ".tmp")
        var patched = 0
        var alreadyFixed = 0
        app.bufferedReader().use { input ->
            tmp.bufferedWriter().use { out ->
                input.lineSequence().forEach { line ->
                    when {
                        line.contains(broken) -> { out.write(line.replace(broken, fixed)); patched++ }
                        line.contains(fixed) -> { out.write(line); alreadyFixed++ }
                        else -> out.write(line)
                    }
                    out.write("\n")
                }
            }
        }
        if (patched + alreadyFixed == 0) {
            tmp.delete()
            throw GradleException("$app: TeaVM's Long_fromNumber changed; update the long-cast fix in build.gradle.kts")
        }
        tmp.renameTo(app)
        println("Long_fromNumber fix applied to $app")
    }
}
