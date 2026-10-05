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

// Unit tests that run on the plain JVM (scripts/unit-test): only the browser-free file system logic
// is compiled for them, so they need neither Forge's jars nor TeaVM. Run: gradle unitTest
val unit = sourceSets.create("unit") {
    java.srcDir("src/main/java")
    java.srcDir("src/unit/java")
}
tasks.named<JavaCompile>("compileUnitJava") {
    include("forgeweb/fs/FileStore.java", "forgeweb/fs/Node.java", "forgeweb/fs/FakeHost.java", "forgeweb/fs/*Test.java")
}
dependencies {
    "unitImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "unitImplementation"("org.junit.jupiter:junit-jupiter-api")
    "unitRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine")
    "unitRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
tasks.register<Test>("unitTest") {
    description = "Runs the JVM unit tests in src/unit."
    group = "verification"
    testClassesDirs = unit.output.classesDirs
    classpath = unit.runtimeClasspath
    useJUnitPlatform()
    testLogging { events("failed"); showStandardStreams = false }
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
        // Minified names (TEAVM_OBFUSCATED=true). The release build (pages.yml) turns it on;
        // local builds, CI and SelfTest stay readable unless it is set.
        obfuscated = System.getenv("TEAVM_OBFUSCATED") == "true"
        // TEAVM_SOURCE_MAP=true writes app.js.map next to app.js (scripts/build-web).
        sourceMap = System.getenv("TEAVM_SOURCE_MAP") == "true"
        sourceFilePolicy = org.teavm.gradle.api.SourceFilePolicy.DO_NOTHING
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
        // Found by its body, not by its name: a minified build renames the function (and drops the
        // spaces). Matched without the trailing ";" or ",", because TeaVM ends the definition with
        // either, depending on the output.
        val broken = Regex("""([A-Za-z_$][\w$]*)(\s*=\s*)val\s*=>\s*BigInt\.asIntN\(64,\s*BigInt\(val\s*>=\s*0\s*\?\s*Math\.floor\(val\)\s*:\s*Math\.ceil\(val\)\)\)""")
        val fixedMarker = "BigInt(\"9223372036854775807\")"
        fun fixedBody(prefix: String) = prefix + "val => (val !== val ? BigInt(0) : val >= 9223372036854775807 ? BigInt(\"9223372036854775807\")" +
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
                        broken.containsMatchIn(line) -> {
                            out.write(broken.replace(line) { fixedBody(it.groupValues[1] + it.groupValues[2]) }); patched++
                        }
                        line.contains(fixedMarker) -> { out.write(line); alreadyFixed++ }
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
