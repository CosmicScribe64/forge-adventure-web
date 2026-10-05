package forgeweb.selftest;

import forgeweb.fs.WebFileSystem;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks the web runtime (TeaVM's classlib with our shims, redirects and file system) without
 * Forge, so it compiles in a fraction of the time. Run with scripts/selftest.
 *
 * Each check prints "SELFTEST PASS <name>" or "SELFTEST FAIL <name>: <why>"; the last line is
 * "SELFTEST DONE <passed>/<total>". Add a check for every runtime bug found in the full game.
 */
public class SelfTest {
    interface Check {
        void run() throws Throwable;
    }

    private static WebFileSystem fs;
    private static int passed;
    private static int total;

    public static void main(String[] args) throws Exception {
        forgeweb.compat.ByName.keep();
        forgeweb.compat.UiThread.start();
        fs = WebFileSystem.install("/forge/", "forge-data/manifest.txt", "forge/");

        check("try-with-resources keeps the primary exception", SelfTest::suppressed);
        check("inflate round trip with tiny buffers", SelfTest::inflateRoundTrip);
        check("availableProcessors is 1", () -> expect(Runtime.getRuntime().availableProcessors() == 1,
                "got " + Runtime.getRuntime().availableProcessors()));
        check("executor invokeAll + CountDownLatch", SelfTest::executorLatch);
        check("failing task surfaces through Future.get", SelfTest::failingTask);
        check("ObjectInputStream fails with IOException", SelfTest::serialization);
        check("every card script in cardsfolder.zip reads", SelfTest::cardsZip);
        check("packed folders read back whole, in one download", SelfTest::packs);
        check("big files and packs read again after the idle trim", SelfTest::trimmedReads);
        check("File.list(filter) finds the adventure planes", SelfTest::listPlanes);
        check("./res resolves against the working directory", SelfTest::relativeRes);
        check("libGDX Json reads adventure config.json (reflection)", SelfTest::adventureJson);
        check("every class in adventure ui_skin.json loads (reflection)", SelfTest::skinClasses);
        check("textratypist effect constructors (reflection)", SelfTest::textEffects);
        check("Thread.sleep from a browser callback doesn't crash", SelfTest::sleepInCallback);
        check("UI-thread task can block on a CompletableFuture", SelfTest::uiThreadJoin);
        check("UUID.randomUUID works on a plain-http page", () -> {
            java.util.UUID u = java.util.UUID.randomUUID();
            expect(u.toString().charAt(14) == '4' && !u.equals(java.util.UUID.randomUUID()), "got " + u);
        });
        check("Pixmap.drawPixmap is cheap on a big pixmap (world minimap)", SelfTest::pixmapDraws);
        check("Adventure save values round-trip (SaveFileData / object streams)", SelfTest::saveRoundTrip);
        check("save hooks are callable (WorldSaveHeader, PaperCard, Deck)", () -> {
            int header = forgeweb.compat.SerialHooks.hookCount(forge.adventure.world.WorldSaveHeader.class);
            int card = forgeweb.compat.SerialHooks.hookCount(forge.item.PaperCard.class);
            int deck = forgeweb.compat.SerialHooks.hookCount(forge.deck.Deck.class);
            expect(header == 2 && card == 2 && deck == 1, "hooks: header " + header + ", card " + card + ", deck " + deck);
        });
        check("save header round-trips (Load screen list)", SelfTest::saveHeaderRoundTrip);
        check("JDK-format object streams fail with IOException", SelfTest::jdkStreamRejected);
        check("rules engine: every trigger/replacement/keyword/effect/AI class constructs", SelfTest::engineReflection);
        check("Thread.getStackTrace has frames to index (FThreads.assertExecutedByEdt)", () -> {
            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            expect(trace.length >= 3 && trace[2].getClassName() != null, "length " + trace.length);
        });
        check("BlockingDeque push/peek/pop and blocking take (Forge's InputQueue)", SelfTest::blockingDeque);
        check("deck-list regex (CardPool.processCardList)", () -> {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("((\\d+)\\s+)?(.*?)").matcher("4 Cinder Barrens|M19|1");
            boolean ok = m.matches();
            System.out.println("  matches=" + ok + (ok ? " count=" + m.group(2) + " card=" + m.group(3) : ""));
            expect(ok && "4".equals(m.group(2)) && "Cinder Barrens|M19|1".equals(m.group(3)), "deck line not parsed");
            java.util.List<org.apache.commons.lang3.tuple.Pair<String, Integer>> cards =
                    forge.deck.CardPool.processCardList(java.util.Arrays.asList("4 Cinder Barrens|M19|1", "Mountain", "# comment"));
            expect(cards.size() == 2 && cards.get(0).getRight() == 4 && "Mountain".equals(cards.get(1).getLeft()), "processCardList " + cards);
        });
        check("starter deck file reads into sections (DeckSerializer.fromFile path)", () -> {
            java.io.File f = new java.io.File("./res/adventure/common/decks/starter/Adventure - Low Rakdos.dck");
            List<String> lines = forge.util.FileUtil.readFile(f);
            java.util.Map<String, List<String>> sections = forge.util.FileSection.parseSections(lines);
            List<String> main = sections.get("Main");
            System.out.println("  exists=" + f.exists() + " lines=" + lines.size() + " sections=" + sections.keySet()
                    + " main=" + (main == null ? null : main.size()));
            expect(main != null && main.size() > 5, "main section " + main);
        });
        check("EnumMap keyed by a Forge enum keeps entries (Deck.parts)", () -> {
            java.util.Map<forge.deck.DeckSection, String> m = new java.util.EnumMap<>(forge.deck.DeckSection.class);
            m.put(forge.deck.DeckSection.Main, "main");
            m.put(forge.deck.DeckSection.Sideboard, "side");
            Object[] constants = forge.deck.DeckSection.class.getEnumConstants();
            System.out.println("  size=" + m.size() + " get(Main)=" + m.get(forge.deck.DeckSection.Main)
                    + " enumConstants=" + (constants == null ? null : constants.length));
            expect(m.size() == 2 && "main".equals(m.get(forge.deck.DeckSection.Main)), "EnumMap lost entries");
            forge.deck.Deck d = new forge.deck.Deck("t");
            d.getOrCreate(forge.deck.DeckSection.Main);
            expect(d.get(forge.deck.DeckSection.Main) != null, "Deck lost its Main section");
        });
        check("card database loads and a starter deck resolves to cards", SelfTest::cardDbAndDeck);
        check("a saved deck reads back with its cards' rules (boosters, PaperCard.readObject)", SelfTest::deckRoundTrip);
        check("AI vs AI match plays to a finish (rules engine end to end)", SelfTest::aiMatch);
        check("AI vs AI match with Adventure's rules (GameType.Adventure, forVariants)", SelfTest::aiMatchAdventureRules);
        check("gdx-controllers stub loads by name (reflection)", () -> {
            Object m = com.badlogic.gdx.utils.reflect.ClassReflection.newInstance(
                    com.badlogic.gdx.utils.reflect.ClassReflection.forName(forge.web.WebLauncher.CONTROLLER_MANAGER));
            expect(m instanceof com.badlogic.gdx.controllers.ControllerManager, "got " + m);
        });
        // Forge packs its 248 frames into one 11488x6480 texture, which crashes the tab.
        check("effects/demo.gif is left out of the web data", () ->
                expect(!new java.io.File("/forge/res/effects/demo.gif").exists(), "demo.gif is in the manifest"));

        // TeaVM passed NaN and infinity to BigInt(), which throws, so resizing the window crashed the game.
        check("(long) casts of NaN, infinities and huge doubles behave as in Java", () -> {
            double[] in = runtimeDoubles(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e30, -1e30, -2.7, 2.7);
            long[] want = {0L, Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, -2L, 2L};
            for (int i = 0; i < in.length; i++) {
                expect((long) in[i] == want[i], "(long) " + in[i] + " = " + (long) in[i]);
                expect((long) (float) in[i] == want[i], "(long) (float) " + in[i] + " = " + (long) (float) in[i]);
            }
            expect(Math.round(Double.NaN) == 0L, "Math.round(NaN) = " + Math.round(Double.NaN));
        });
        check("DeflaterOutputStream (native zlib) reads back with InflaterInputStream", SelfTest::deflateRoundTrip);
        check("small synchronized method while another thread is suspended holding the lock", SelfTest::borrowedMonitor);
        check("every thread queued on a held monitor eventually enters it", SelfTest::contendedMonitor);
        check("world generation benchmark (wave-function collapse, as World.generateNew)", SelfTest::wfcBenchmark);

        System.out.println("SELFTEST DONE " + passed + "/" + total);
    }

    /** Values the compiler can't fold into constants. */
    private static double[] runtimeDoubles(double... values) {
        return values.clone();
    }

    private static void check(String name, Check c) {
        total++;
        long start = System.currentTimeMillis();
        try {
            c.run();
            passed++;
            System.out.println("SELFTEST PASS " + name + " (" + (System.currentTimeMillis() - start) + " ms)");
        } catch (Throwable t) {
            System.out.println("SELFTEST FAIL " + name + ": " + t);
            t.printStackTrace();
        }
    }

    private static void expect(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
    }

    // TeaVM 0.15: Throwable.addSuppressed crashed and hid the primary exception.
    private static void suppressed() throws Exception {
        try (AutoCloseable r = () -> {
            throw new IOException("close");
        }) {
            throw new IOException("body");
        } catch (IOException e) {
            expect("body".equals(e.getMessage()), "caught " + e);
        }
    }

    // TeaVM 0.15: Inflater threw on Z_BUF_ERROR instead of returning 0.
    private static void inflateRoundTrip() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) sb.append("line ").append(i * 7919 % 1000).append('\n');
        byte[] plain = sb.toString().getBytes(StandardCharsets.UTF_8);
        for (boolean nowrap : new boolean[] {false, true}) {
            Deflater d = new Deflater(6, nowrap);
            d.setInput(plain);
            d.finish();
            ByteArrayOutputStream packed = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            while (!d.finished()) packed.write(buf, 0, d.deflate(buf));
            byte[] data = packed.toByteArray();
            if (nowrap) data = java.util.Arrays.copyOf(data, data.length + 1); // JDK's dummy byte
            try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(data), new Inflater(nowrap), 37)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] small = new byte[13];
                int n;
                while ((n = in.read(small)) > 0) out.write(small, 0, n);
                expect(java.util.Arrays.equals(plain, out.toByteArray()),
                        "nowrap=" + nowrap + ": got " + out.size() + " of " + plain.length + " bytes");
            }
        }
    }

    // The card loader's pattern: invokeAll on a pool, then await a latch.
    private static void executorLatch() throws Exception {
        CountDownLatch latch = new CountDownLatch(8);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int k = i;
            tasks.add(() -> {
                Thread.sleep(5);
                latch.countDown();
                return k;
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        int sum = 0;
        for (Future<Integer> f : pool.invokeAll(tasks)) sum += f.get();
        pool.shutdown();
        latch.await();
        expect(sum == 28, "sum " + sum);
    }

    private static void failingTask() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<Object> f = pool.submit(() -> {
            throw new IllegalStateException("boom");
        });
        pool.shutdown();
        try {
            f.get();
            throw new AssertionError("no exception");
        } catch (ExecutionException e) {
            expect(e.getCause() instanceof IllegalStateException, "cause " + e.getCause());
        }
    }

    // Serialization is stubbed on web; callers rely on a catchable IOException.
    private static void serialization() {
        try {
            new ObjectInputStream(new ByteArrayInputStream(new byte[] {(byte) 0xAC, (byte) 0xED, 0, 5})).readObject();
            throw new AssertionError("readObject returned");
        } catch (IOException expected) {
            // ok
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    // scripts/build-webdata packs editions/ etc.; each file must read back at its manifest size.
    private static void packs() throws IOException {
        int before = fs.remoteFetches();
        java.io.File dir = new java.io.File("/forge/res/editions");
        String[] names = dir.list();
        expect(names != null && names.length > 500, "editions: " + (names == null ? null : names.length));
        for (String name : names) {
            java.io.File f = new java.io.File(dir, name);
            if (f.isDirectory()) continue;
            byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
            expect(data.length == f.length(), name + ": read " + data.length + " of " + f.length());
            expect(new String(data, StandardCharsets.UTF_8).contains("[metadata]"), name + ": no [metadata]");
        }
        int fetched = fs.remoteFetches() - before;
        expect(fetched == 1, fetched + " downloads for " + names.length + " files");
    }

    // WebFileSystem drops whole packs and big read-only files when idle; an open zip, a file in a
    // pack that was not read yet, and a file read before must all still come back.
    private static void trimmedReads() throws Exception {
        try (ZipFile zip = new ZipFile("/forge/res/cardsfolder/cardsfolder.zip")) {
            java.util.zip.ZipEntry entry = zip.entries().nextElement();
            byte[] first = zip.getInputStream(entry).readAllBytes();
            java.io.File ed = new java.io.File("/forge/res/editions");
            String[] names = ed.list();
            byte[] one = java.nio.file.Files.readAllBytes(new java.io.File(ed, names[0]).toPath());
            fs.trimNow();
            int before = fs.remoteFetches();
            byte[] again = zip.getInputStream(entry).readAllBytes();
            expect(java.util.Arrays.equals(first, again), "zip entry differs after trim");
            expect(fs.remoteFetches() == before + 1, "zip downloaded " + (fs.remoteFetches() - before) + " times");
            expect(java.util.Arrays.equals(one, java.nio.file.Files.readAllBytes(new java.io.File(ed, names[0]).toPath())),
                    "pack file differs after trim");
            java.io.File unread = new java.io.File("/forge/res/formats/Archived/Alchemy/2022-02-10.txt");
            byte[] other = java.nio.file.Files.readAllBytes(unread.toPath());
            expect(other.length == 127, "unread pack file after trim: " + other.length + " bytes");
        }
    }

    // A Skin JSON names the classes it creates (in full, or by a libGDX tag); each needs reflection.
    private static void skinClasses() throws Exception {
        com.badlogic.gdx.utils.JsonValue root = new com.badlogic.gdx.utils.JsonReader().parse(
                new com.badlogic.gdx.files.FileHandle("/forge/res/adventure/common/skin/ui_skin.json"));
        com.badlogic.gdx.utils.ObjectMap<String, Class> tags = new com.badlogic.gdx.scenes.scene2d.ui.Skin().getJsonClassTags();
        List<String> missing = new ArrayList<>();
        int n = 0;
        for (com.badlogic.gdx.utils.JsonValue entry = root.child; entry != null; entry = entry.next) {
            n++;
            Class<?> tagged = tags.get(entry.name);
            try {
                Class<?> c = tagged != null ? tagged : com.badlogic.gdx.utils.reflect.ClassReflection.forName(entry.name);
                // Skin builds fonts itself (not by reflection), and they need a GL context.
                if (c != com.badlogic.gdx.graphics.g2d.BitmapFont.class) {
                    com.badlogic.gdx.utils.reflect.ClassReflection.newInstance(c);
                }
            } catch (Throwable t) {
                missing.add(entry.name + " (" + t + (t.getCause() != null ? " <- " + t.getCause() : "") + ")");
            }
        }
        System.out.println("  " + n + " skin classes");
        expect(missing.isEmpty(), "missing: " + missing);
    }

    // TypingLabel's Parser builds each {EFFECT} tag's class through its reflected constructors.
    private static void textEffects() {
        List<String> broken = new ArrayList<>();
        List<Class<? extends com.github.tommyettinger.textra.Effect>> effects =
                com.github.tommyettinger.textra.SelfTestAccess.effectClasses();
        for (Class<?> c : effects) {
            try {
                com.badlogic.gdx.utils.reflect.Constructor[] ctors = com.badlogic.gdx.utils.reflect.ClassReflection.getConstructors(c);
                if (ctors.length == 0) {
                    broken.add(c.getSimpleName() + " (no constructors)");
                }
                for (com.badlogic.gdx.utils.reflect.Constructor k : ctors) k.getParameterTypes();
            } catch (Throwable t) {
                broken.add(c.getSimpleName() + " (" + t + ")");
            }
        }
        System.out.println("  " + effects.size() + " effects");
        expect(effects.size() > 10 && broken.isEmpty(), "broken: " + broken);
    }

    @org.teavm.jso.JSFunctor
    interface Callback extends org.teavm.jso.JSObject {
        void run();
    }

    @org.teavm.jso.JSBody(params = "f", script = "setTimeout(f, 0);")
    private static native void setTimeout(Callback f);

    private static volatile String callbackResult;

    // Forge's button sound sleeps 30 ms inside the click handler (no green thread there).
    private static void sleepInCallback() throws InterruptedException {
        callbackResult = null;
        setTimeout(() -> {
            try {
                Thread.sleep(30);
                callbackResult = "ok";
            } catch (Throwable t) {
                callbackResult = t.toString();
            }
        });
        for (int i = 0; i < 50 && callbackResult == null; i++) Thread.sleep(20);
        expect("ok".equals(callbackResult), "callback: " + callbackResult);
    }

    // World.generateNew joins CompletableFutures on the UI thread; a browser callback posts it there.
    private static void uiThreadJoin() throws InterruptedException {
        callbackResult = null;
        setTimeout(() -> forgeweb.compat.UiThread.post(() -> {
            try {
                int v = java.util.concurrent.CompletableFuture.supplyAsync(() -> 40)
                        .thenApply(x -> x + 2).join();
                callbackResult = v == 42 ? "ok" : "got " + v;
            } catch (Throwable t) {
                callbackResult = t.toString();
            }
        }));
        for (int i = 0; i < 100 && callbackResult == null; i++) Thread.sleep(20);
        expect("ok".equals(callbackResult), "UI task: " + callbackResult);
    }

    @org.teavm.jso.JSBody(script = "if (!window.__gdxLoading) { window.__gdxLoading = true;"
            + " var s = document.createElement('script'); s.src = 'scripts/gdx.wasm.js'; document.head.appendChild(s); }"
            + " return !!(window.Gdx && window.Gdx.HEAP8);")
    private static native boolean gdxWasmReady();

    // World.generateNew draws 700x700 4px tiles into one 2800x2800 pixmap; gdx-teavm copied the
    // whole pixmap out of wasm memory after every draw.
    private static void pixmapDraws() throws InterruptedException {
        for (int i = 0; i < 250 && !gdxWasmReady(); i++) Thread.sleep(20);
        expect(gdxWasmReady(), "gdx.wasm.js did not load");
        com.badlogic.gdx.graphics.Pixmap big = new com.badlogic.gdx.graphics.Pixmap(2800, 2800, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        com.badlogic.gdx.graphics.Pixmap tile = new com.badlogic.gdx.graphics.Pixmap(4, 4, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        tile.setColor(0x11223344);
        tile.fill();
        long start = System.currentTimeMillis();
        for (int i = 0; i < 20000; i++) {
            big.drawPixmap(tile, (i % 700) * 4, (i / 700) * 4);
        }
        long ms = System.currentTimeMillis() - start;
        java.nio.ByteBuffer px = big.getPixels();
        int r = px.get(0) & 0xff;
        System.out.println("  20000 draws in " + ms + " ms; first pixel red=" + r);
        expect(r == 0x11 && big.getPixel(4, 4) == 0x11223344, "pixels not updated: red=" + r + " pixel=" + Integer.toHexString(big.getPixel(4, 4)));
        expect(ms < 3000, "too slow: " + ms + " ms");
        big.dispose();
        tile.dispose();
    }

    // What Adventure's autosave stores (PointOfInterestChanges, SpritesDataMap, World, AdventurePlayer ...).
    @SuppressWarnings("unchecked")
    private static void saveRoundTrip() throws Exception {
        forge.adventure.util.SaveFileData data = new forge.adventure.util.SaveFileData();
        java.util.HashMap<Integer, java.util.HashSet<Integer>> bought = new java.util.HashMap<>();
        bought.put(7, new java.util.HashSet<>(java.util.Arrays.asList(1, 2, 3)));
        long[][] biome = {{1L, 2L}, {3L, Long.MAX_VALUE}};
        java.util.List<org.apache.commons.lang3.tuple.Pair<com.badlogic.gdx.math.Vector2, Integer>>[][] objects = new java.util.List[2][1];
        objects[1][0] = new java.util.ArrayList<>(java.util.Collections.singletonList(
                org.apache.commons.lang3.tuple.Pair.of(new com.badlogic.gdx.math.Vector2(1.5f, -2f), 42)));
        java.util.Map<String, Byte> flags = new java.util.TreeMap<>();
        flags.put("door", (byte) 3);
        forge.adventure.data.ItemData item = new forge.adventure.data.ItemData();
        item.name = "Rune";
        item.usableOnWorldMap = true;
        data.storeObject("bought", bought);
        data.storeObject("biome", biome);
        data.storeObject("objects", objects);
        data.storeObject("flags", flags);
        data.storeObject("items", new forge.adventure.data.ItemData[] {item, item});
        data.storeObject("equipped", new Long[] {5L, null});
        data.storeObject("date", new java.util.Date(123456789L));
        java.util.UUID id = java.util.UUID.randomUUID();
        data.storeObject("id", id);
        data.store("gold", 250);
        data.store("x", 1.25f);
        data.store("name", "Reth");
        forge.adventure.util.SaveFileData sub = new forge.adventure.util.SaveFileData();
        sub.store("depth", 2L);
        data.store("sub", sub);

        // The whole map again, as WorldSave writes it after the header.
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(data);
        }
        forge.adventure.util.SaveFileData back;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            back = (forge.adventure.util.SaveFileData) in.readObject();
        }
        System.out.println("  save map: " + bytes.size() + " bytes, " + back.size() + " keys");

        expect(back.readObject("bought").equals(bought), "bought " + back.readObject("bought"));
        Object biomeBack = back.readObject("biome");
        // (TeaVM's Arrays.deepEquals doesn't compare primitive inner arrays by value.)
        expect(biomeBack instanceof long[][] && ((long[][]) biomeBack).length == 2
                && java.util.Arrays.equals(((long[][]) biomeBack)[0], biome[0])
                && java.util.Arrays.equals(((long[][]) biomeBack)[1], biome[1]),
                "biome: " + (biomeBack == null ? null : biomeBack.getClass().getName() + " "
                + (biomeBack instanceof Object[] ? java.util.Arrays.deepToString((Object[]) biomeBack) : biomeBack)));
        java.util.List<org.apache.commons.lang3.tuple.Pair<com.badlogic.gdx.math.Vector2, Integer>>[][] o2 =
                (java.util.List<org.apache.commons.lang3.tuple.Pair<com.badlogic.gdx.math.Vector2, Integer>>[][]) back.readObject("objects");
        expect(o2[0][0] == null && o2[1][0].get(0).getLeft().y == -2f && o2[1][0].get(0).getRight() == 42, "objects");
        expect(back.readObject("flags").equals(flags), "flags " + back.readObject("flags"));
        forge.adventure.data.ItemData[] items = (forge.adventure.data.ItemData[]) back.readObject("items");
        expect(items[0] == items[1] && "Rune".equals(items[0].name) && items[0].usableOnWorldMap, "items");
        Long[] eq = (Long[]) back.readObject("equipped");
        expect(eq[0] == 5L && eq[1] == null, "equipped");
        expect(((java.util.Date) back.readObject("date")).getTime() == 123456789L, "date");
        expect(id.equals(back.readObject("id")), "uuid " + back.readObject("id"));
        expect(!back.containsKey("IOException"), "a store failed: " + back.readString("IOException"));
        expect(back.readInt("gold") == 250 && back.readFloat("x") == 1.25f && "Reth".equals(back.readString("name")), "primitives");
        expect(back.readSubData("sub").readLong("depth") == 2L, "sub data");
    }

    private static void jdkStreamRejected() {
        byte[] jdk = {(byte) 0xAC, (byte) 0xED, 0, 5, 0x74, 0, 1, 0x41};
        try {
            new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(jdk)).readObject();
            throw new AssertionError("read a JDK stream");
        } catch (java.io.IOException expected) {
            // ok
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    // Forge builds triggers, replacement effects, keywords, spell effects and their AI through
    // reflection; a class without reflection metadata breaks the whole enum (TriggerType looks its
    // constructors up in its own constructor) or silently degrades (Keyword falls back to SimpleKeyword).
    private static void engineReflection() {
        List<String> broken = new ArrayList<>();
        try {
            forge.game.trigger.TriggerType.values();
        } catch (Throwable t) {
            broken.add("TriggerType: " + t);
        }
        for (Class<?> c : forge.game.replacement.SelfTestAccess.replacementClasses()) {
            boolean found = false;
            for (java.lang.reflect.Constructor<?> k : c.getDeclaredConstructors()) {
                Class<?>[] p = k.getParameterTypes();
                if (p.length > 0 && p[0].isAssignableFrom(java.util.Map.class)) found = true;
            }
            if (!found) broken.add("replacement " + c.getName());
        }
        for (Class<?> c : forge.game.keyword.SelfTestAccess.keywordClasses()) {
            try {
                c.getConstructor().newInstance();
            } catch (Throwable t) {
                broken.add("keyword " + c.getName() + ": " + t);
            }
        }
        int apis = 0;
        try {
            for (forge.game.ability.ApiType api : forge.game.ability.ApiType.values()) {
                apis++;
                if (api.getSpellEffect() == null) broken.add("effect " + api);
                try {
                    if (forge.ai.SpellApiToAi.Converter.get(api) == null) broken.add("ai " + api);
                } catch (Throwable t) {
                    broken.add("ai " + api + ": " + t);
                }
            }
        } catch (Throwable t) {
            broken.add("ApiType: " + t);
        }
        System.out.println("  " + apis + " spell APIs checked");
        expect(broken.isEmpty(), broken.size() + " broken: " + broken.subList(0, Math.min(8, broken.size())));
    }

    private static void blockingDeque() throws Exception {
        java.util.concurrent.BlockingDeque<String> stack = new java.util.concurrent.LinkedBlockingDeque<>();
        stack.push("a");
        stack.push("b");
        expect("b".equals(stack.peek()) && "b".equals(stack.pop()) && stack.size() == 1, "stack ops");
        java.util.concurrent.BlockingDeque<String> q = new java.util.concurrent.LinkedBlockingDeque<>();
        Thread producer = new Thread(() -> {
            try {
                Thread.sleep(30);
                q.putLast("x");
            } catch (InterruptedException ignored) {
                // done
            }
        });
        producer.start();
        expect("x".equals(q.take()), "take");
        expect(q.pollFirst(20, java.util.concurrent.TimeUnit.MILLISECONDS) == null, "poll timeout");
    }

    private static boolean cardDbLoaded;

    /** The card database, as FModel.initialize builds it (Lang, Localizer, ImageKeys first). */
    // As WorldSave.save writes the header and SaveLoadScene.updateFiles reads it back. With the
    // hooks missing the streams wrote no fields at all, and the Load screen crashed on a null name.
    private static void saveHeaderRoundTrip() throws Exception {
        for (int i = 0; i < 250 && !gdxWasmReady(); i++) Thread.sleep(20);
        forge.adventure.world.WorldSaveHeader header = new forge.adventure.world.WorldSaveHeader();
        header.name = "auto save\u2502Forest";
        header.saveDate = new java.util.Date(987654321L);
        header.preview = new com.badlogic.gdx.graphics.Pixmap(3, 2, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        header.preview.drawPixel(2, 1, 0x11223344);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream def = new java.util.zip.DeflaterOutputStream(bytes);
             java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(def)) {
            out.writeObject(header);
        }
        forge.adventure.world.WorldSaveHeader back;
        try (java.util.zip.InflaterInputStream inf = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()));
             java.io.ObjectInputStream in = new java.io.ObjectInputStream(inf)) {
            back = (forge.adventure.world.WorldSaveHeader) in.readObject();
        }
        expect(header.name.equals(back.name), "name " + back.name);
        expect(back.saveDate != null && back.saveDate.getTime() == 987654321L, "date " + back.saveDate);
        expect(back.preview != null && back.preview.getWidth() == 3 && back.preview.getHeight() == 2
                && back.preview.getPixel(2, 1) == 0x11223344, "preview " + back.preview);
        header.dispose();
        back.dispose();
    }

    private static void deckRoundTrip() throws Exception {
        ensureCardDb();
        forge.deck.Deck deck = forge.deck.io.DeckSerializer.fromFile(
                new java.io.File("./res/adventure/common/decks/starter/Adventure - Low Rakdos.dck"));
        forge.adventure.util.SaveFileData data = new forge.adventure.util.SaveFileData();
        data.storeObject("boosters", new forge.deck.Deck[] {deck});
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
            out.writeObject(data);
        }
        forge.adventure.util.SaveFileData back;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
            back = (forge.adventure.util.SaveFileData) in.readObject();
        }
        expect(!back.containsKey("IOException"), "store failed: " + back.readString("IOException"));
        forge.deck.Deck deckBack = ((forge.deck.Deck[]) back.readObject("boosters"))[0];
        int total = deckBack.getMain().countAll(), noRules = 0;
        for (java.util.Map.Entry<forge.item.PaperCard, Integer> e : deckBack.getMain()) {
            if (e.getKey().getRules() == null) noRules++;
        }
        System.out.println("  deck: " + bytes.size() + " bytes, " + total + " cards, " + noRules + " without rules");
        expect(total == deck.getMain().countAll() && noRules == 0, total + " cards, " + noRules + " without rules");
        expect(java.util.Objects.equals(deck.getName(), deckBack.getName()), "name " + deckBack.getName());
    }

    private static void ensureCardDb() {
        if (cardDbLoaded) return;
        cardDbLoaded = true;
        long start = System.currentTimeMillis();
        // As FModel.initialize does before loading cards.
        forge.util.Lang.createInstance("en-US");
        forge.ImageKeys.initializeDirs("/forge/cache/pics/cards/", new java.util.HashMap<>(), "/forge/cache/pics/tokens/",
                "/forge/cache/pics/icons/", "/forge/cache/pics/boosters/", "/forge/cache/pics/fatpacks/",
                "/forge/cache/pics/boosterboxes/", "/forge/cache/pics/precons/", "/forge/cache/pics/tournamentpacks/");
        forge.util.Localizer.getInstance().initialize("en-US", "/forge/res/languages/");
        forge.CardStorageReader reader = new forge.CardStorageReader("/forge/res/cardsfolder/", null, false);
        forge.CardStorageReader tokenReader = new forge.CardStorageReader("/forge/res/tokenscripts/", null, false);
        new forge.StaticData(reader, tokenReader, null, null, "/forge/res/editions/", "/forge/data/custom/editions/",
                "/forge/res/blockdata/", "/forge/res/setlookup/", "LATEST_ART_ALL_EDITIONS", true, false, false, false);
        int cards = forge.StaticData.instance().getCommonCards().getAllCards().size();
        System.out.println("  card db: " + cards + " cards in " + (System.currentTimeMillis() - start) + " ms");
        expect(cards > 30000, "only " + cards + " cards");

    }

    // What a duel needs: the card database (as FModel builds it) and a starter deck whose Main
    // section resolves to real, supported cards (the first duel had empty libraries).
    private static void cardDbAndDeck() {
        ensureCardDb();
        forge.deck.Deck deck = forge.deck.io.DeckSerializer.fromFile(
                new java.io.File("./res/adventure/common/decks/starter/Adventure - Low Rakdos.dck"));
        forge.deck.CardPool main = deck.getMain();
        int total = main == null ? -1 : main.countAll();
        int unsupported = 0;
        if (main != null) {
            for (java.util.Map.Entry<forge.item.PaperCard, Integer> e : main) {
                if (e.getKey().getRules() == null || e.getKey().getRules().isUnsupported()) unsupported++;
            }
        }
        int valid = deck.getValid().getLeft().getMain().countAll();
        System.out.println("  deck: " + total + " cards, " + unsupported + " unsupported, " + valid + " valid");
        expect(total >= 30 && unsupported == 0 && valid == total, "deck main " + total + ", unsupported " + unsupported + ", valid " + valid);
    }

    // A whole game between two AIs with Adventure starter decks: libraries, draws, mulligans,
    // spells, combat, triggers, game end. Like forge-gui-desktop's SimulateMatch.
    private static void aiMatch() {
        playAiMatch(false);
    }

    // The same, set up the way Adventure's DuelScene does (GameType.Adventure, forVariants, rules).
    private static void aiMatchAdventureRules() {
        playAiMatch(true);
    }

    private static void playAiMatch(boolean adventure) {
        ensureCardDb();
        forge.ai.AiProfileUtil.loadAllProfiles("/forge/res/ai/");
        forge.deck.Deck d1 = forge.deck.io.DeckSerializer.fromFile(
                new java.io.File("./res/adventure/common/decks/starter/Adventure - Low Rakdos.dck"));
        forge.deck.Deck d2 = forge.deck.io.DeckSerializer.fromFile(
                new java.io.File("./res/adventure/common/decks/starter/Adventure - Low Selesnya.dck"));
        forge.game.GameType type = adventure ? forge.game.GameType.Adventure : forge.game.GameType.Constructed;
        forge.game.GameRules rules = new forge.game.GameRules(type);
        rules.setGamesPerMatch(1);
        java.util.Set<forge.game.GameType> variants = java.util.EnumSet.of(type);
        if (adventure) {
            rules.setAppliedVariants(variants);
            rules.setManaBurn(false);
            rules.setWarnAboutAICards(false);
        }
        List<forge.game.player.RegisteredPlayer> players = new ArrayList<>();
        forge.game.player.RegisteredPlayer p1 = adventure
                ? forge.game.player.RegisteredPlayer.forVariants(2, variants, d1, null, false, null, null)
                : new forge.game.player.RegisteredPlayer(d1);
        p1.setPlayer(new forge.ai.LobbyPlayerAi("Rakdos", null));
        forge.game.player.RegisteredPlayer p2 = adventure
                ? forge.game.player.RegisteredPlayer.forVariants(2, variants, d2, null, false, null, null)
                : new forge.game.player.RegisteredPlayer(d2);
        if (adventure) p2.setStartingLife(15);
        p2.setPlayer(new forge.ai.LobbyPlayerAi("Selesnya", null));
        players.add(p1);
        players.add(p2);
        forge.game.Match match = new forge.game.Match(rules, players, "SelfTest");
        forge.game.Game game = match.createGame();
        game.setNoGUIUser();
        long start = System.currentTimeMillis();
        match.startGame(game);
        long ms = System.currentTimeMillis() - start;
        int turns = game.getPhaseHandler().getTurn();
        StringBuilder libs = new StringBuilder();
        for (forge.game.player.Player p : game.getPlayers()) {
            libs.append(p.getName()).append(": life ").append(p.getLife())
                    .append(", library ").append(p.getCardsIn(forge.game.zone.ZoneType.Library).size())
                    .append(", graveyard ").append(p.getCardsIn(forge.game.zone.ZoneType.Graveyard).size()).append("; ");
        }
        String winner = game.getOutcome() == null || game.getOutcome().isDraw() ? "draw"
                : game.getOutcome().getWinningLobbyPlayer().getName();
        int logEntries = game.getGameLog().getLogEntries(null).size();
        System.out.println("  " + turns + " turns in " + ms + " ms, winner " + winner + ", " + logEntries + " log entries; " + libs);
        expect(game.isGameOver() && turns >= 3, "game over " + game.isGameOver() + " after " + turns + " turns");
        // The log is written by an event subscriber: no entries means game events aren't delivered
        // (and the duel screen, fed the same way, wouldn't update).
        expect(logEntries > 10, "game log has " + logEntries + " entries");

        // Conceding: the UI thread holds the game's lock in setGameOver while events suspend it, and
        // the game thread asks isGameOver() meanwhile. TeaVM compiles small synchronized methods
        // with a lock that throws on contention instead of waiting.
        Thread holder = new Thread(() -> {
            synchronized (game) {
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    // done
                }
            }
        });
        holder.start();
        try {
            Thread.sleep(20);
        } catch (InterruptedException ignored) {
            // go on
        }
        String contended;
        try {
            contended = "isGameOver=" + game.isGameOver();
        } catch (Throwable t) {
            contended = t.toString();
        }
        expect(contended.startsWith("isGameOver="), "isGameOver() while another thread holds the game: " + contended);
    }

    // Adventure's Config lists plane folders this way (res/adventure/Shandalar, ...).
    private static void listPlanes() {
        java.io.File dir = new java.io.File("/forge/res/adventure");
        String[] all = dir.list();
        String[] planes = dir.list((file, s) -> !s.contains(".") && !s.equals("common"));
        System.out.println("  exists=" + dir.exists() + " isDirectory=" + dir.isDirectory()
                + " list()=" + java.util.Arrays.toString(all) + " list(filter)=" + java.util.Arrays.toString(planes));
        expect(planes != null && java.util.Arrays.asList(planes).contains("Shandalar"), "no Shandalar");
    }

    // Adventure's Config.resPath() finds res/ relative to the working directory with java.nio.
    private static void deflateRoundTrip() throws Exception {
        byte[] data = new byte[3 * 1024 * 1024];
        for (int i = 0; i < data.length; i++) data[i] = (byte) ((i * 31) ^ (i >> 9)); // compressible, not trivial
        long start = System.currentTimeMillis();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream out = new java.util.zip.DeflaterOutputStream(bytes)) {
            for (int off = 0; off < data.length; off += 1000) out.write(data, off, Math.min(1000, data.length - off));
        }
        long ms = System.currentTimeMillis() - start;
        byte[] packed = bytes.toByteArray();
        java.io.ByteArrayOutputStream back = new java.io.ByteArrayOutputStream();
        try (java.util.zip.InflaterInputStream in = new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(packed))) {
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0; ) back.write(b, 0, n);
        }
        System.out.println("BENCH deflate 3 MB in " + ms + " ms -> " + packed.length + " bytes");
        expect(java.util.Arrays.equals(data, back.toByteArray()), "round trip differs");

        // A caller's Deflater (as PixmapIO.PNG does) and GZIP (a nowrap Deflater with a trailer).
        java.io.ByteArrayOutputStream custom = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream out = new java.util.zip.DeflaterOutputStream(custom,
                new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED))) {
            out.write(data, 0, 100000);
        }
        byte[] c = inflate(new java.util.zip.InflaterInputStream(new java.io.ByteArrayInputStream(custom.toByteArray())));
        expect(java.util.Arrays.equals(java.util.Arrays.copyOf(data, 100000), c), "custom Deflater round trip differs");
        java.io.ByteArrayOutputStream gz = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream out = new java.util.zip.GZIPOutputStream(gz)) {
            out.write(data, 0, 100000);
        }
        byte[] g = inflate(new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz.toByteArray())));
        expect(java.util.Arrays.equals(java.util.Arrays.copyOf(data, 100000), g), "GZIP round trip differs");
        java.io.ByteArrayOutputStream gz2 = new java.io.ByteArrayOutputStream();
        java.util.zip.GZIPOutputStream twice = new java.util.zip.GZIPOutputStream(gz2);
        twice.write(data, 0, 100000);
        twice.finish();
        twice.close();
        twice.close();
        expect(java.util.Arrays.equals(gz.toByteArray(), gz2.toByteArray()), "finish+close+close differs from close: "
                + gz.size() + " vs " + gz2.size() + " bytes");
    }

    private static byte[] inflate(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream back = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream i = in) {
            byte[] b = new byte[8192];
            for (int n; (n = i.read(b)) > 0; ) back.write(b, 0, n);
        }
        return back.toByteArray();
    }

    static final class Counter {
        private int value;

        synchronized int get() { // no suspension point: TeaVM's non-blocking monitor entry
            return value;
        }

        synchronized void poke() { // no suspension point either, and notifies
            notifyAll();
        }

        synchronized void setSlowly(int v) throws InterruptedException {
            value = v;
            Thread.sleep(200); // suspends while holding the lock
        }
    }

    private static void borrowedMonitor() throws Exception {
        Counter c = new Counter();
        Thread holder = new Thread(() -> {
            try {
                c.setSlowly(7);
            } catch (InterruptedException ignored) {
            }
        });
        holder.start();
        Thread.sleep(50); // holder is now asleep inside setSlowly
        int seen = c.get();
        int again = c.get(); // borrowed entry must also exit cleanly
        c.poke(); // notifyAll while borrowing must not throw
        holder.join();
        synchronized (c) { // and the lock must still work normally afterwards
            expect(seen == 7 && again == 7 && c.get() == 7, "seen " + seen + "/" + again);
        }
    }

    /** One thread sleeps holding a lock while four more queue to enter it; all must get in. */
    private static void contendedMonitor() throws Exception {
        final Object lock = new Object();
        final java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(4);
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                try {
                    Thread.sleep(150); // suspends while holding the lock
                } catch (InterruptedException ignored) {
                }
            }
        });
        holder.start();
        Thread.sleep(30);
        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                synchronized (lock) {
                    entered.countDown();
                }
            }).start();
        }
        boolean all = entered.await(3, java.util.concurrent.TimeUnit.SECONDS);
        expect(all, "only " + (4 - entered.getCount()) + " of 4 contenders entered the monitor");
    }

    /**
     * Forge's own WFC on a real structure model, sized like World.generateNew's 10x10 chunks.
     * Prints the time as "BENCH wfc <ms>" to compare backends and optimisations.
     */
    private static void wfcBenchmark() throws InterruptedException {
        for (int i = 0; i < 250 && !gdxWasmReady(); i++) Thread.sleep(20);
        String path = "/forge/res/adventure/common/world/structures/models/red.png";
        // As ColorMap(FileHandle) does, but decoding the bytes directly (no Gdx.app here).
        byte[] png = new com.badlogic.gdx.files.FileHandle(path).readBytes();
        com.badlogic.gdx.graphics.Pixmap pix = new com.badlogic.gdx.graphics.Pixmap(png, 0, png.length);
        forge.adventure.world.ColorMap source = new forge.adventure.world.ColorMap(pix.getWidth(), pix.getHeight());
        for (int y = 0; y < pix.getHeight(); y++) {
            for (int x = 0; x < pix.getWidth(); x++) {
                source.setColor(x, y, new com.badlogic.gdx.graphics.Color(pix.getPixel(x, y)));
            }
        }
        pix.dispose();
        expect(source.getWidth() == 16, "model width " + source.getWidth());
        long start = System.currentTimeMillis();
        int ok = 0;
        for (int i = 0; i < 200; i++) {
            forge.adventure.world.OverlappingModel model =
                    new forge.adventure.world.OverlappingModel(source, 2, 10, 10, true, true, 8, 0);
            if (model.run(1234 + i * 5355, 0)) ok++;
        }
        long ms = System.currentTimeMillis() - start;
        System.out.println("BENCH wfc " + ms + " ms (200 models, " + ok + " succeeded)");
        expect(ok > 0, "no model succeeded");

        // BiomeStructure reuses one model per chunk size (Forge patch): the output must be exactly
        // what a fresh model gives for the same seed, including after failed runs.
        forge.adventure.world.OverlappingModel reused =
                new forge.adventure.world.OverlappingModel(source, 2, 10, 10, true, true, 8, 0);
        start = System.currentTimeMillis();
        for (int i = 0; i < 200; i++) {
            reused.run(1234 + i * 5355, 0);
        }
        System.out.println("BENCH wfc-reused " + (System.currentTimeMillis() - start) + " ms (200 runs)");
        for (int i = 0; i < 40; i++) {
            int seed = 99 + i * 5355;
            forge.adventure.world.OverlappingModel fresh =
                    new forge.adventure.world.OverlappingModel(source, 2, 10, 10, true, true, 8, 0);
            boolean a = fresh.run(seed, 0), b = reused.run(seed, 0);
            expect(a == b, "seed " + seed + ": fresh " + a + ", reused " + b);
            if (!a) continue;
            forge.adventure.world.ColorMap fa = fresh.graphics(), fb = reused.graphics();
            for (int y = 0; y < 10; y++) {
                for (int x = 0; x < 10; x++) {
                    expect(fa.getColor(x, y).equals(fb.getColor(x, y)), "seed " + seed + " differs at " + x + "," + y);
                }
            }
        }
    }

    private static void relativeRes() {
        boolean nio = java.nio.file.Files.exists(java.nio.file.Paths.get("./res"));
        boolean io = new java.io.File("./res").exists();
        String[] rel = new java.io.File("./res/adventure").list();
        System.out.println("  user.dir=" + System.getProperty("user.dir") + " nio ./res=" + nio + " io ./res=" + io
                + " ./res/adventure=" + (rel == null ? null : rel.length + " entries"));
        expect(nio, "Files.exists(./res) is false");
    }

    // Adventure loads all its data with libGDX Json, which needs TeaVM reflection metadata.
    private static void adventureJson() {
        forge.adventure.data.ConfigData c = new com.badlogic.gdx.utils.Json().fromJson(
                forge.adventure.data.ConfigData.class,
                new com.badlogic.gdx.files.FileHandle("/forge/res/adventure/common/config.json"));
        expect(c.screenWidth > 0, "screenWidth " + c.screenWidth);
    }

    // TeaVM 0.15: ZipFile gave the Inflater no dummy byte, so some entries hit EOFException.
    private static void cardsZip() throws IOException {
        int entries = 0;
        long chars = 0;
        try (ZipFile zip = new ZipFile("/forge/res/cardsfolder/cardsfolder.zip")) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(zip.getInputStream(e), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) chars += line.length() + 1;
                } catch (IOException ex) {
                    throw new IOException(e.getName() + ": " + ex, ex);
                }
                entries++;
            }
        }
        expect(entries > 30000, "only " + entries + " entries");
        System.out.println("  read " + entries + " card scripts, " + chars + " chars");
    }
}
