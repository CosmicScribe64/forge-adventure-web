package forgeweb.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.character.PlayerSprite;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.pointofintrest.PointOfInterest;
import forge.adventure.scene.Scene;
import forge.adventure.scene.WebTestAccess;
import forge.adventure.stage.GameStage;
import forge.adventure.stage.PointOfInterestMapSprite;
import forge.adventure.stage.WorldStage;
import forge.adventure.world.WorldSave;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.player.PlayerControllerHuman;
import forge.screens.match.MatchController;
import forge.screens.match.views.VPrompt;
import forge.toolbox.FButton;
import forge.toolbox.FContainer;
import forge.toolbox.FDisplayObject;
import forge.toolbox.FOverlay;
import forge.util.ITriggerEvent;
import forgeweb.compat.UiThread;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Test harness one level above key presses and pixel clicks: window.forgeTest.cmd("...")
 * returns JSON. Commands run on the UI thread (UiThread) between frames.
 *
 *   state                      scene, player position, HUD, nearby entities/POIs, buttons, texts, duel
 *   moveto X Y                 walk there: A* over the stage's own collision check, then follow
 *   goto NAME                  walk into the nearest entity or point of interest whose name matches
 *   stop                       cancel walking
 *   click TEXT                 press the visible button whose text or name matches (scene2d or Forge UI)
 *   layout                     the HUD's world size and element bounds (layout debugging)
 *   display                    the game's size and the framebuffer's: {w, h, bw, bh} (webtest step display-check)
 *   console TEXT               Forge's Adventure console (for example "spawn enemy Clay Golem")
 *   dismiss                    click through a chain of one-button dialogs (tutorial/NPC messages);
 *                              returns their texts, and the open dialog's choices if it has several
 *   duel                       duel state: phase, life, hands, battlefields, prompt, buttons
 *   ok | cancel                the duel prompt's buttons
 *   play NAME | select NAME    select a card (hand, battlefield) the way tapping it would
 *   player NAME                select a player (targets)
 *   attackall                  declare every possible attacker
 *
 * Walking uses the game's joystick input (GameStage.setTouchKnobInput), so collisions, speed and
 * encounters behave exactly as for a player. See scripts/play.
 */
public final class WebTest {
    private static List<Vector2> path = Collections.emptyList();
    private static int pathIndex;
    private static String pathStatus = "idle";
    private static Vector2 pathGoal;
    private static Object pathTarget; // Actor or PointOfInterest being walked into, if any
    private static long lastRetarget;
    private static boolean pathTouch;
    private static Scene pathScene;
    private static final Vector2 lastPos = new Vector2();
    private static long lastProgressAt;
    private static int replans;

    private WebTest() {
    }

    @JSFunctor
    interface Resolver extends JSObject {
        void resolve(String json);
    }

    @JSFunctor
    interface Handler extends JSObject {
        void handle(String command, Resolver resolver);
    }

    /** Only on pages opened with a {@code test} parameter (the test tools add ?test=1). */
    public static void install() {
        if (!testPage()) return;
        installImpl((command, resolver) -> UiThread.post(() -> {
            String result;
            try {
                result = execute(command == null ? "" : command.trim());
            } catch (Throwable t) {
                result = "{\"error\":" + q(String.valueOf(t)) + "}";
            }
            if (result == DEFERRED) {
                deferred = resolver; // finished by tick() over the next frames
            } else {
                resolver.resolve(result);
            }
        }));
    }

    @JSBody(script = "return new URLSearchParams(location.search).has('test');")
    private static native boolean testPage();

    @JSBody(params = "handler", script = "window.forgeTest = { cmd: function(c) {"
            + " return new Promise(function(res) { handler(c, res); }); } };")
    private static native void installImpl(Handler handler);

    // --- dialogs (GameStage's dialog stage: tutorial, NPC and quest messages) ---

    private static final String DEFERRED = new String("deferred");
    private static Resolver deferred;
    private static final List<String> dismissed = new ArrayList<>();
    private static int dismissQuietFrames, dismissFrames;

    /** The dialog stage if a dialog is showing on the current map, else null. */
    static Stage openDialog() {
        Scene scene = Forge.getCurrentScene();
        if (scene == null) return null;
        for (Stage stage : WebTestAccess.uiStagesOf(scene)) {
            for (Actor a : stage.getRoot().getChildren()) {
                if (a.isVisible() && a instanceof com.badlogic.gdx.scenes.scene2d.ui.Dialog) return stage;
            }
        }
        return null;
    }

    /** The open dialog's text and choices (only Dialog actors: the HUD shares the dialog stage). */
    static String dialogJson() {
        Stage stage = openDialog();
        List<String> choices = new ArrayList<>(), texts = new ArrayList<>();
        if (stage != null) {
            for (Actor a : stage.getRoot().getChildren()) {
                if (!a.isVisible() || !(a instanceof com.badlogic.gdx.scenes.scene2d.ui.Dialog)) continue;
                com.badlogic.gdx.scenes.scene2d.ui.Dialog d = (com.badlogic.gdx.scenes.scene2d.ui.Dialog) a;
                collectUi(d.getContentTable(), new ArrayList<>(), texts);
                List<Button> buttons = new ArrayList<>();
                visibleButtons(d.getButtonTable(), buttons);
                Collections.reverse(buttons);
                for (Button b : buttons) choices.add(buttonText(b));
            }
        }
        texts.removeIf(t -> t.trim().isEmpty());
        return "{\"texts\":" + list(texts) + ",\"choices\":" + list(choices) + "}";
    }

    static String startDismiss() {
        if (deferred != null) return "{\"error\":\"a dismiss is already running\"}";
        dismissed.clear();
        dismissQuietFrames = 0;
        dismissFrames = 0;
        return DEFERRED;
    }

    /**
     * One frame of dismiss: if the dialog offers exactly one choice (Continue, OK, ...), press it;
     * stop when it offers several (the caller picks with click), when pressing changed nothing, or
     * after 20 frames without a dialog.
     */
    private static void tickDismiss() {
        Stage stage = openDialog();
        dismissFrames++;
        if (stage == null) {
            if (++dismissQuietFrames >= 20 || dismissFrames > 600) finishDismiss(null);
            return;
        }
        dismissQuietFrames = 0;
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = null;
        for (Actor a : stage.getRoot().getChildren()) {
            if (a.isVisible() && a instanceof com.badlogic.gdx.scenes.scene2d.ui.Dialog) {
                dialog = (com.badlogic.gdx.scenes.scene2d.ui.Dialog) a;
            }
        }
        if (dialog == null) return;
        List<Button> choices = new ArrayList<>();
        visibleButtons(dialog.getButtonTable(), choices);
        Collections.reverse(choices); // top-to-bottom order
        List<String> buttons = new ArrayList<>(), texts = new ArrayList<>();
        collectUi(dialog.getContentTable(), buttons, texts);
        String text = String.join(" ", texts).trim();
        if (choices.isEmpty()) return; // still animating in
        if (choices.size() > 1 || dismissFrames > 600) {
            List<String> names = new ArrayList<>();
            for (Button b : choices) names.add(buttonText(b));
            finishDismiss("{\"text\":" + q(text) + ",\"choices\":" + list(names) + "}");
            return;
        }
        String entry = text + " [" + buttonText(choices.get(0)) + "]";
        if (!dismissed.isEmpty() && dismissed.get(dismissed.size() - 1).equals(entry) && dismissFrames - lastPressFrame < 30) {
            return; // the press is still taking effect (dialogs close with an animation)
        }
        if (!dismissed.isEmpty() && dismissed.get(dismissed.size() - 1).equals(entry)) {
            finishDismiss("{\"text\":" + q(text) + ",\"stuck\":true}");
            return;
        }
        dismissed.add(entry);
        lastPressFrame = dismissFrames;
        Button b = choices.get(0);
        Vector2 c = b.localToStageCoordinates(new Vector2(b.getWidth() / 2, b.getHeight() / 2));
        Vector2 sc = stage.stageToScreenCoordinates(c);
        stage.touchDown((int) sc.x, (int) sc.y, 0, 0);
        stage.touchUp((int) sc.x, (int) sc.y, 0, 0);
    }

    private static int lastPressFrame;

    private static void finishDismiss(String open) {
        Resolver r = deferred;
        deferred = null;
        r.resolve("{\"dismissed\":" + list(dismissed) + (open == null ? "" : ",\"open\":" + open) + "}");
    }


    // --- commands ---

    /** addcards N: puts N different cards of the Adventure card pool into the player's collection
     *  (in a fixed order), so the deck editor has a long list of card images to scroll through. */
    private static String addCards(String arg) {
        int n = Integer.parseInt(arg.isEmpty() ? "100" : arg);
        if (WorldSave.getCurrentSave() == null) return "{\"error\":\"no game\"}";
        String[] editions = forge.adventure.util.Config.instance().getConfigData().allowedEditions;
        List<String> allowed = editions == null ? Collections.<String>emptyList() : java.util.Arrays.asList(editions);
        List<forge.item.PaperCard> pool = new ArrayList<>();
        for (forge.item.PaperCard c : forge.model.FModel.getMagicDb().getCommonCards().getAllCards()) {
            if (allowed.isEmpty() || allowed.contains(c.getEdition())) pool.add(c);
        }
        Collections.sort(pool);
        Collections.shuffle(pool, new java.util.Random(1));
        AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
        int added = Math.min(n, pool.size());
        for (int i = 0; i < added; i++) player.addCard(pool.get(i));
        return "{\"added\":" + added + ",\"pool\":" + pool.size() + "}";
    }

    static String execute(String command) {
        String[] parts = command.split("\\s+", 2);
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";
        switch (verb) {
            case "state": return state();
            case "addcards": return addCards(arg);
            case "fsstats": {
                Object fs = org.teavm.runtime.fs.VirtualFileSystemProvider.getInstance();
                return fs instanceof forgeweb.fs.WebFileSystem ? ((forgeweb.fs.WebFileSystem) fs).cappedStats() : "{}";
            }
            case "moveto": {
                String[] xy = arg.split("[\\s,]+");
                return moveTo(Float.parseFloat(xy[0]), Float.parseFloat(xy[1]), false);
            }
            case "goto":
            case "interact": return goTo(arg);
            case "stop": stopWalking("stopped"); return "{\"ok\":true}";
            case "click": return click(arg);
            case "where": return where(arg);
            case "dismiss": return startDismiss();
            case "display": return "{\"w\":" + Gdx.graphics.getWidth() + ",\"h\":" + Gdx.graphics.getHeight()
                    + ",\"bw\":" + Gdx.graphics.getBackBufferWidth() + ",\"bh\":" + Gdx.graphics.getBackBufferHeight() + "}";
            case "console": return q(forge.adventure.stage.ConsoleCommandInterpreter.getInstance().command(arg));
            case "layout": return forge.adventure.stage.WebTestStageAccess.hudLayout();
            case "duel": return duelState();
            case "ok": return duelButton(true);
            case "cancel": return duelButton(false);
            case "play":
            case "select": return selectCard(arg);
            case "player": return selectPlayer(arg);
            case "attackall": return attackAll();
            default: return "{\"error\":" + q("unknown command: " + verb) + "}";
        }
    }

    // --- state ---

    static String state() {
        StringBuilder sb = new StringBuilder("{");
        Scene scene = Forge.getCurrentScene();
        field(sb, "scene", scene == null ? null : scene.getClass().getSimpleName());
        Stage stage = scene == null ? null : WebTestAccess.stageOf(scene);
        field(sb, "stage", stage == null ? null : stage.getClass().getSimpleName());
        AdventurePlayer ap = AdventurePlayer.current();
        if (ap != null) {
            sb.append("\"hud\":{\"life\":").append(ap.getLife()).append(",\"maxLife\":").append(ap.getMaxLife())
                    .append(",\"gold\":").append(ap.getGold()).append(",\"shards\":").append(ap.getShards()).append("},");
        }
        sb.append("\"path\":{\"status\":").append(q(pathStatus)).append(",\"remaining\":")
                .append(Math.max(0, path.size() - pathIndex)).append("},");
        if (stage instanceof GameStage) {
            GameStage gs = (GameStage) stage;
            PlayerSprite player = gs.getPlayerSprite();
            Vector2 p = player.pos();
            sb.append("\"player\":{\"x\":").append(r(p.x)).append(",\"y\":").append(r(p.y)).append("},");
            List<String[]> entities = new ArrayList<>();
            collectEntities(gs.getSpriteGroup(), p, entities);
            entities.sort((a, b) -> Float.compare(Float.parseFloat(a[4]), Float.parseFloat(b[4])));
            sb.append("\"entities\":[");
            for (int i = 0; i < entities.size() && i < 20; i++) {
                String[] e = entities.get(i);
                if (i > 0) sb.append(',');
                sb.append("{\"kind\":").append(q(e[0])).append(",\"name\":").append(q(e[1]))
                        .append(",\"x\":").append(e[2]).append(",\"y\":").append(e[3]).append(",\"dist\":").append(e[4]).append('}');
            }
            sb.append("],");
            if (gs instanceof WorldStage && WorldSave.getCurrentSave() != null) {
                List<PointOfInterest> pois = new ArrayList<>(WorldSave.getCurrentSave().getWorld().getAllPointOfInterest());
                pois.sort((a, b) -> Float.compare(a.getPosition().dst(p), b.getPosition().dst(p)));
                sb.append("\"pois\":[");
                for (int i = 0; i < pois.size() && i < 12; i++) {
                    PointOfInterest poi = pois.get(i);
                    if (i > 0) sb.append(',');
                    sb.append("{\"name\":").append(q(poi.getDisplayName())).append(",\"type\":").append(q(poi.getData().type))
                            .append(",\"x\":").append(r(poi.getPosition().x)).append(",\"y\":").append(r(poi.getPosition().y))
                            .append(",\"dist\":").append(r(poi.getPosition().dst(p))).append('}');
                }
                sb.append("],");
            }
        }
        List<String> buttons = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        if (scene != null) for (Stage ui : WebTestAccess.uiStagesOf(scene)) collectUi(ui.getRoot(), buttons, texts);
        collectForgeButtons(buttons);
        sb.append("\"buttons\":").append(list(buttons)).append(",\"texts\":").append(list(texts));
        if (openDialog() != null) sb.append(",\"dialog\":").append(dialogJson());
        if (duelGame() != null) sb.append(",\"duel\":").append(duelState());
        return sb.append('}').toString();
    }

    private static void collectEntities(Group group, Vector2 p, List<String[]> out) {
        for (Actor a : group.getChildren()) {
            if (!a.isVisible() || a instanceof PlayerSprite) continue;
            String kind = a.getClass().getSimpleName();
            String name = a.getName();
            if (a instanceof EnemySprite) {
                name = ((EnemySprite) a).getName();
            } else if (a instanceof PointOfInterestMapSprite) {
                name = ((PointOfInterestMapSprite) a).getPointOfInterest().getDisplayName();
            }
            if (!(a instanceof EnemySprite) && !(a instanceof PointOfInterestMapSprite)
                    && !kind.endsWith("Actor") && !kind.endsWith("Sprite")) continue;
            float d = Vector2.dst(p.x, p.y, a.getX(), a.getY());
            // [kind, name, x, y, distance, centre x, centre y]
            out.add(new String[] {kind, name == null ? "" : name, r(a.getX()), r(a.getY()), r(d),
                    r(a.getX() + a.getWidth() / 2), r(a.getY() + a.getHeight() / 2)});
        }
    }

    private static void collectUi(Actor a, List<String> buttons, List<String> texts) {
        if (a == null || !a.isVisible()) return;
        if (a instanceof Button) {
            String t = buttonText((Button) a);
            if (!t.isEmpty()) buttons.add(t);
        } else if (a instanceof TextraLabel) {
            String t = ((TextraLabel) a).storedText;
            if (t != null && !t.trim().isEmpty() && texts.size() < 30) texts.add(stripMarkup(t));
        } else if (a instanceof Label) {
            String t = ((Label) a).getText().toString();
            if (!t.trim().isEmpty() && texts.size() < 30) texts.add(t);
        }
        if (a instanceof Group) {
            for (Actor c : ((Group) a).getChildren()) collectUi(c, buttons, texts);
        }
    }

    private static String buttonText(Button b) {
        String t = "";
        if (b instanceof TextraButton) t = ((TextraButton) b).getText();
        else if (b instanceof TextButton) t = ((TextButton) b).getText().toString();
        t = t == null ? "" : stripMarkup(t).trim();
        if (t.isEmpty() && b.getName() != null) t = b.getName();
        return t;
    }

    private static void collectForgeButtons(List<String> out) {
        List<FButton> all = new ArrayList<>();
        forgeButtons(all);
        for (FButton b : all) out.add("[forge] " + b.getText());
    }

    private static void forgeButtons(List<FButton> out) {
        for (FOverlay o : FOverlay.getOverlaysTopDown()) {
            if (o.isVisible()) forgeButtons(o, out);
        }
        if (Forge.getCurrentScreen() != null && duelGame() == null) forgeButtons(Forge.getCurrentScreen(), out);
    }

    private static void forgeButtons(FDisplayObject d, List<FButton> out) {
        if (d == null || !d.isVisible()) return;
        if (d instanceof FButton && d.isEnabled() && ((FButton) d).getText() != null && !((FButton) d).getText().isEmpty()) {
            out.add((FButton) d);
        }
        if (d instanceof FContainer) {
            for (FDisplayObject c : ((FContainer) d).getChildren()) forgeButtons(c, out);
        }
    }

    // --- clicking ---

    static String click(String text) {
        String want = text.toLowerCase(Locale.ROOT);
        Scene scene = Forge.getCurrentScene();
        for (Stage stage : scene == null ? new ArrayList<Stage>() : WebTestAccess.uiStagesOf(scene)) {
            Button b = findButton(stage.getRoot(), want);
            if (b != null) {
                Vector2 c = b.localToStageCoordinates(new Vector2(b.getWidth() / 2, b.getHeight() / 2));
                Vector2 s = stage.stageToScreenCoordinates(c);
                stage.touchDown((int) s.x, (int) s.y, 0, 0);
                stage.touchUp((int) s.x, (int) s.y, 0, 0);
                return "{\"clicked\":" + q(buttonText(b)) + "}";
            }
        }
        List<FButton> fbs = new ArrayList<>();
        forgeButtons(fbs);
        for (FButton fb : fbs) {
            if (fb.getText().toLowerCase(Locale.ROOT).contains(want)) {
                fb.tap(fb.getWidth() / 2, fb.getHeight() / 2, 1);
                return "{\"clicked\":" + q("[forge] " + fb.getText()) + "}";
            }
        }
        return "{\"error\":" + q("no visible button matching '" + text + "'") + "}";
    }

    /**
     * Where a visible button's centre is on screen, as {"x":..,"y":..,"w":..,"h":..} in the canvas's own pixels
     * (libGDX screen coordinates, y down; w and h are the canvas size), so a test can send a real tap or click
     * through the browser's input path instead of calling the stage as {@link #click} does.
     */
    static String where(String text) {
        String want = text.toLowerCase(Locale.ROOT);
        Scene scene = Forge.getCurrentScene();
        for (Stage stage : scene == null ? new ArrayList<Stage>() : WebTestAccess.uiStagesOf(scene)) {
            Button b = findButton(stage.getRoot(), want);
            if (b != null) {
                Vector2 c = b.localToStageCoordinates(new Vector2(b.getWidth() / 2, b.getHeight() / 2));
                Vector2 s = stage.stageToScreenCoordinates(c);
                return "{\"x\":" + s.x + ",\"y\":" + s.y + ",\"w\":" + Gdx.graphics.getWidth()
                        + ",\"h\":" + Gdx.graphics.getHeight() + "}";
            }
        }
        return "{\"error\":" + q("no visible button matching '" + text + "'") + "}";
    }

    /** The visible button whose text (or name) is {@code want}; failing that, one containing it. */
    private static Button findButton(Actor root, String want) {
        List<Button> all = new ArrayList<>();
        visibleButtons(root, all);
        for (Button b : all) {
            if (buttonText(b).toLowerCase(Locale.ROOT).equals(want)
                    || (b.getName() != null && b.getName().toLowerCase(Locale.ROOT).equals(want))) return b;
        }
        for (Button b : all) {
            if (buttonText(b).toLowerCase(Locale.ROOT).contains(want)) return b;
        }
        return null;
    }

    /** Visible buttons, topmost (last drawn) first: dialogs are added after the scene UI. */
    private static void visibleButtons(Actor a, List<Button> out) {
        if (a == null || !a.isVisible()) return;
        if (a instanceof Group) {
            List<Actor> kids = new ArrayList<>();
            for (Actor c : ((Group) a).getChildren()) kids.add(c);
            for (int i = kids.size() - 1; i >= 0; i--) visibleButtons(kids.get(i), out);
        }
        if (a instanceof Button && !buttonText((Button) a).isEmpty()) out.add((Button) a);
    }

    // --- walking ---

    static String goTo(String name) {
        GameStage gs = gameStage();
        if (gs == null) return "{\"error\":\"not on a map\"}";
        if (openDialog() != null) return "{\"error\":\"a dialog is open (dismiss it first)\",\"dialog\":" + dialogJson() + "}";
        String want = name.toLowerCase(Locale.ROOT);
        Vector2 p = gs.getPlayerSprite().pos();
        Actor bestActor = null;
        float bestDist = Float.MAX_VALUE;
        for (Actor a : gs.getSpriteGroup().getChildren()) {
            if (!a.isVisible() || a instanceof PlayerSprite) continue;
            String kind = a.getClass().getSimpleName();
            if (!(a instanceof EnemySprite) && !(a instanceof PointOfInterestMapSprite)
                    && !kind.endsWith("Actor") && !kind.endsWith("Sprite")) continue;
            String n = entityName(a);
            float d = Vector2.dst(p.x, p.y, a.getX(), a.getY());
            if ((n + " " + kind).toLowerCase(Locale.ROOT).contains(want) && d < bestDist) {
                bestActor = a;
                bestDist = d;
            }
        }
        // Aim at the centre: walking into its box is what triggers it. The target (and only it)
        // isn't an obstacle, and a moving one is followed (see tick).
        if (bestActor != null) {
            return moveTo(bestActor.getX() + bestActor.getWidth() / 2, bestActor.getY() + bestActor.getHeight() / 2,
                    true, bestActor);
        }

        if (gs instanceof WorldStage && WorldSave.getCurrentSave() != null) {
            PointOfInterest bestPoi = null;
            for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
                String n = (poi.getDisplayName() + " " + poi.getData().type).toLowerCase(Locale.ROOT);
                if (n.contains(want) && (bestPoi == null || poi.getPosition().dst(p) < bestPoi.getPosition().dst(p))) bestPoi = poi;
            }
            if (bestPoi != null) return moveTo(bestPoi.getCenter().x, bestPoi.getCenter().y, true, bestPoi);
        }
        return "{\"error\":" + q("nothing named '" + name + "' nearby") + "}";
    }

    /** @param touch the goal is something to walk into (entity, door): end exactly on it. */
    static String moveTo(float gx, float gy, boolean touch) {
        return moveTo(gx, gy, touch, null);
    }

    /** target: the Actor or PointOfInterest walked into (not an obstacle), or null. */
    static String moveTo(float gx, float gy, boolean touch, Object target) {
        pathTarget = target;
        GameStage gs = gameStage();
        if (gs == null) return "{\"error\":\"not on a map\"}";
        if (openDialog() != null) return "{\"error\":\"a dialog is open (dismiss it first)\",\"dialog\":" + dialogJson() + "}";
        List<Vector2> planned = plan(gs, gx, gy, touch);
        if (planned == null) {
            stopWalking("no path");
            return "{\"error\":\"no walkable path\",\"path\":0}";
        }
        path = planned;
        pathIndex = 0;
        pathGoal = new Vector2(gx, gy);
        pathTouch = touch;
        pathScene = Forge.getCurrentScene();
        pathStatus = "walking";
        lastPos.set(gs.getPlayerSprite().pos());
        lastProgressAt = System.currentTimeMillis();
        replans = 0;
        return "{\"ok\":true,\"waypoints\":" + planned.size() + "}";
    }

    static void stopWalking(String status) {
        GameStage gs = gameStage();
        if (gs != null) gs.setTouchKnobInput(0, 0);
        path = Collections.emptyList();
        pathIndex = 0;
        pathStatus = status;
    }

    /** Called before every frame (MainThreadListener): steers the player along the path. */
    public static void tick() {
        if (deferred != null) tickDismiss();
        if (!"walking".equals(pathStatus)) return;
        if (openDialog() != null) {
            // The game stops the player while a dialog is up; report it instead of "stuck".
            stopWalking("interrupted by dialog");
            return;
        }
        if (Forge.getCurrentScene() != pathScene) {
            path = Collections.emptyList();
            pathStatus = "arrived (scene changed)";
            return;
        }
        GameStage gs = gameStage();
        if (gs == null) return;
        Vector2 p = gs.getPlayerSprite().pos();
        if (pathTarget instanceof Actor) {
            // A moving target (an enemy wandering or chasing): plan again when it has moved.
            Actor t = (Actor) pathTarget;
            float tx = t.getX() + t.getWidth() / 2, ty = t.getY() + t.getHeight() / 2;
            long now = System.currentTimeMillis();
            if (t.getStage() == null) {
                stopWalking("target gone");
                return;
            }
            if (pathGoal.dst(tx, ty) > 12 && now - lastRetarget > 400) {
                lastRetarget = now;
                List<Vector2> again = plan(gs, tx, ty, true);
                if (again != null) {
                    path = again;
                    pathIndex = 0;
                    pathGoal.set(tx, ty);
                }
            }
        }
        while (pathIndex < path.size() && path.get(pathIndex).dst(p) < 3f) pathIndex++;
        if (pathIndex >= path.size()) {
            stopWalking("arrived");
            return;
        }
        Vector2 wp = path.get(pathIndex);
        float dx = wp.x - p.x, dy = wp.y - p.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        gs.setTouchKnobInput(dx / len, dy / len);
        long now = System.currentTimeMillis();
        if (p.dst(lastPos) > 1f) {
            lastPos.set(p);
            lastProgressAt = now;
        } else if (now - lastProgressAt > 1500) {
            // Blocked (a moving sprite, a dialog, rounding at a corner): plan again from here.
            if (replans++ < 3) {
                List<Vector2> again = plan(gs, pathGoal.x, pathGoal.y, pathTouch);
                if (again != null) {
                    path = again;
                    pathIndex = 0;
                    lastProgressAt = now;
                    return;
                }
            }
            stopWalking("stuck");
        }
    }

    /**
     * A* on a grid over the stage, testing the player's own bounding box against
     * GameStage.isColliding (tiles on the world map, collision objects on town/cave maps).
     */
    private static boolean isTarget(Actor a) {
        if (pathTarget == null) return false;
        return a == pathTarget || (a instanceof PointOfInterestMapSprite
                && ((PointOfInterestMapSprite) a).getPointOfInterest() == pathTarget);
    }

    private static String entityName(Actor a) {
        if (a instanceof EnemySprite) return String.valueOf(((EnemySprite) a).getName());
        if (a instanceof PointOfInterestMapSprite) return ((PointOfInterestMapSprite) a).getPointOfInterest().getDisplayName();
        return a.getName() == null ? "" : a.getName();
    }

    static List<Vector2> plan(GameStage gs, float gx, float gy, boolean touch) {
        PlayerSprite player = gs.getPlayerSprite();
        Rectangle box = player.boundingRect();
        Vector2 start = player.pos();
        float offX = box.x - start.x, offY = box.y - start.y;
        float step = gs instanceof WorldStage ? 8f : 4f;
        Rectangle probe = new Rectangle();
        // Things that act when touched (towns/caves/dungeons, enemies, NPCs, doors) are obstacles,
        // except the target and whatever the player already stands on (to walk off it).
        List<Rectangle> blockers = new ArrayList<>();
        // Obstacles the player starts inside may only be walked out of, never back into:
        // the game re-triggers a POI once the player has left its rectangle.
        List<Rectangle> exitOnly = new ArrayList<>();
        Rectangle here = new Rectangle(box);
        for (Actor a : gs.getSpriteGroup().getChildren()) {
            if (!a.isVisible() || a instanceof PlayerSprite) continue;
            String kind = a.getClass().getSimpleName();
            if (!(a instanceof EnemySprite) && !(a instanceof PointOfInterestMapSprite) && !kind.endsWith("Actor")) continue;
            Rectangle rb = new Rectangle(a.getX(), a.getY(), Math.max(a.getWidth(), 8), Math.max(a.getHeight(), 8));
            if (isTarget(a)) continue;
            if (rb.overlaps(here)) {
                exitOnly.add(rb);
                continue;
            }
            blockers.add(rb);
        }
        // On the world map only nearby chunks have sprites; every town, cave and dungeon along a
        // long route is an obstacle too (walking over one enters it).
        if (gs instanceof WorldStage && WorldSave.getCurrentSave() != null) {
            for (PointOfInterest poi : WorldSave.getCurrentSave().getWorld().getAllPointOfInterest()) {
                if (!poi.getActive()) continue;
                Rectangle rb = new Rectangle(poi.getBoundingRectangle());
                if (poi == pathTarget) continue;
                if (rb.overlaps(here)) {
                    if (!exitOnly.contains(rb)) exitOnly.add(rb);
                    continue;
                }
                blockers.add(rb);
            }
        }
        // Free-cell test for a player standing at (x, y).
        java.util.function.BiPredicate<Float, Float> free = (x, y) -> {
            probe.set(x + offX, y + offY, box.width, box.height);
            if (gs.isColliding(probe)) return false;
            for (Rectangle rb : blockers) {
                if (rb.overlaps(probe)) return false;
            }
            return true;
        };
        // Bitmask of the exit-only rectangles a player standing at (x, y) overlaps.
        java.util.function.BiFunction<Float, Float, Integer> inside = (x, y) -> {
            Rectangle r = new Rectangle(x + offX, y + offY, box.width, box.height);
            int m = 0;
            for (int i = 0; i < exitOnly.size() && i < 31; i++) {
                if (exitOnly.get(i).overlaps(r)) m |= 1 << i;
            }
            return m;
        };
        int sx = Math.round(start.x / step), sy = Math.round(start.y / step);
        int tx = Math.round(gx / step), ty = Math.round(gy / step);
        // A goal inside an obstacle (an entity standing in a wall, a door): nearest free cell.
        if (!free.test(tx * step, ty * step)) {
            int[] near = nearestFree(free, tx, ty, step, 12);
            if (near == null) return null;
            tx = near[0];
            ty = near[1];
        }
        int margin = 120;
        int minX = Math.min(sx, tx) - margin, maxX = Math.max(sx, tx) + margin;
        int minY = Math.min(sy, ty) - margin, maxY = Math.max(sy, ty) + margin;
        int w = maxX - minX + 1;
        Map<Integer, Integer> cameFrom = new HashMap<>();
        Map<Integer, Float> cost = new HashMap<>();
        Map<Integer, Boolean> freeCache = new HashMap<>();
        PriorityQueue<float[]> open = new PriorityQueue<>((a, b) -> Float.compare(a[0], b[0]));
        int startKey = (sy - minY) * w + (sx - minX);
        int goalKey = (ty - minY) * w + (tx - minX);
        cost.put(startKey, 0f);
        open.add(new float[] {0, startKey});
        int[] dxs = {1, -1, 0, 0, 1, 1, -1, -1};
        int[] dys = {0, 0, 1, -1, 1, -1, 1, -1};
        int budget = 120000;
        boolean found = false;
        while (!open.isEmpty() && budget-- > 0) {
            int key = (int) open.poll()[1];
            if (key == goalKey) {
                found = true;
                break;
            }
            int cx = key % w + minX, cy = key / w + minY;
            float base = cost.get(key);
            for (int k = 0; k < 8; k++) {
                int nx = cx + dxs[k], ny = cy + dys[k];
                if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
                if (!cellFree(free, freeCache, nx, ny, minX, minY, w, step)) continue;
                if (k >= 4 && (!cellFree(free, freeCache, cx + dxs[k], cy, minX, minY, w, step)
                        || !cellFree(free, freeCache, cx, cy + dys[k], minX, minY, w, step))) continue; // no corner cutting
                if (!exitOnly.isEmpty()) {
                    int from = inside.apply(cx * step, cy * step), to = inside.apply(nx * step, ny * step);
                    if ((to & ~from) != 0) continue; // re-entering something already left
                }
                int nk = (ny - minY) * w + (nx - minX);
                float nc = base + (k >= 4 ? 1.4142f : 1f);
                Float old = cost.get(nk);
                if (old == null || nc < old) {
                    cost.put(nk, nc);
                    cameFrom.put(nk, key);
                    float h = (float) Math.hypot(nx - tx, ny - ty);
                    open.add(new float[] {nc + h, nk});
                }
            }
        }
        if (!found) return null;
        List<Vector2> cells = new ArrayList<>();
        for (Integer k = goalKey; k != null && k != startKey; k = cameFrom.get(k)) {
            cells.add(new Vector2((k % w + minX) * step, (k / w + minY) * step));
        }
        Collections.reverse(cells);
        List<Vector2> simplified = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            if (i == 0 || i == cells.size() - 1) {
                simplified.add(cells.get(i));
                continue;
            }
            Vector2 a = cells.get(i - 1), b = cells.get(i), c = cells.get(i + 1);
            if (Math.signum(b.x - a.x) != Math.signum(c.x - b.x) || Math.signum(b.y - a.y) != Math.signum(c.y - b.y)) {
                simplified.add(b);
            }
        }
        if (touch) simplified.add(new Vector2(gx, gy)); // walk into the target itself
        return simplified;
    }

    private static boolean cellFree(java.util.function.BiPredicate<Float, Float> free, Map<Integer, Boolean> cache,
                                    int x, int y, int minX, int minY, int w, float step) {
        int key = (y - minY) * w + (x - minX);
        Boolean v = cache.get(key);
        if (v == null) {
            v = free.test(x * step, y * step);
            cache.put(key, v);
        }
        return v;
    }

    private static int[] nearestFree(java.util.function.BiPredicate<Float, Float> free, int tx, int ty, float step, int radius) {
        for (int r = 1; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    if (free.test((tx + dx) * step, (ty + dy) * step)) return new int[] {tx + dx, ty + dy};
                }
            }
        }
        return null;
    }

    private static GameStage gameStage() {
        Scene scene = Forge.getCurrentScene();
        Stage stage = scene == null ? null : WebTestAccess.stageOf(scene);
        return stage instanceof GameStage ? (GameStage) stage : null;
    }

    // --- duels ---

    private static Game duelGame() {
        HostedMatch hm = MatchController.getHostedMatch();
        Game g = hm == null ? null : hm.getGame();
        return g == null || g.isGameOver() ? null : g;
    }

    private static PlayerControllerHuman human(Game g) {
        for (Player p : g.getPlayers()) {
            if (p.getController() instanceof PlayerControllerHuman) return (PlayerControllerHuman) p.getController();
        }
        return null;
    }

    static String duelState() {
        Game g = duelGame();
        if (g == null) return "null";
        PlayerControllerHuman hc = human(g);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"turn\":").append(g.getPhaseHandler().getTurn()).append(',');
        field(sb, "phase", String.valueOf(g.getPhaseHandler().getPhase()));
        field(sb, "activePlayer", g.getPhaseHandler().getPlayerTurn() == null ? null : g.getPhaseHandler().getPlayerTurn().getName());
        sb.append("\"players\":[");
        boolean first = true;
        for (Player p : g.getPlayers()) {
            if (!first) sb.append(',');
            first = false;
            boolean me = hc != null && p == hc.getPlayer();
            sb.append("{\"name\":").append(q(p.getName())).append(",\"me\":").append(me)
                    .append(",\"life\":").append(p.getLife())
                    .append(",\"library\":").append(p.getCardsIn(ZoneType.Library).size())
                    .append(",\"graveyard\":").append(p.getCardsIn(ZoneType.Graveyard).size())
                    .append(",\"handSize\":").append(p.getCardsIn(ZoneType.Hand).size());
            if (me) sb.append(",\"hand\":").append(cards(p.getCardsIn(ZoneType.Hand)));
            sb.append(",\"battlefield\":").append(cards(p.getCardsIn(ZoneType.Battlefield))).append('}');
        }
        sb.append("],\"stack\":").append(g.getStack().size());
        if (hc != null && MatchController.getView() != null) {
            VPrompt prompt = MatchController.getView().getPrompt(hc.getPlayer().getView());
            if (prompt != null) {
                sb.append(",\"prompt\":").append(q(prompt.getMessage()))
                        .append(",\"ok\":").append(q(prompt.getBtnOk().isEnabled() ? prompt.getBtnOk().getText() : ""))
                        .append(",\"cancel\":").append(q(prompt.getBtnCancel().isEnabled() ? prompt.getBtnCancel().getText() : ""));
            }
        }
        return sb.append('}').toString();
    }

    private static String cards(Iterable<Card> cs) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Card c : cs) {
            if (!first) sb.append(',');
            first = false;
            sb.append("{\"name\":").append(q(c.getName()));
            if (c.isTapped()) sb.append(",\"tapped\":true");
            if (c.isCreature()) sb.append(",\"pt\":").append(q(c.getNetPower() + "/" + c.getNetToughness()));
            sb.append('}');
        }
        return sb.append(']').toString();
    }

    static String duelButton(boolean ok) {
        Game g = duelGame();
        PlayerControllerHuman hc = g == null ? null : human(g);
        if (hc == null) return "{\"error\":\"no duel\"}";
        if (ok) hc.getInputProxy().selectButtonOK();
        else hc.getInputProxy().selectButtonCancel();
        return "{\"ok\":true}";
    }

    private static final ITriggerEvent TAP = new ITriggerEvent() {
        @Override public int getButton() { return 1; }
        @Override public int getX() { return 0; }
        @Override public int getY() { return 0; }
    };

    static String selectCard(String name) {
        Game g = duelGame();
        PlayerControllerHuman hc = g == null ? null : human(g);
        if (hc == null) return "{\"error\":\"no duel\"}";
        String want = name.toLowerCase(Locale.ROOT);
        Card best = null;
        // Own hand first, then any battlefield (targets), then own graveyard.
        List<Card> candidates = new ArrayList<>();
        for (Card c : hc.getPlayer().getCardsIn(ZoneType.Hand)) candidates.add(c);
        for (Player p : g.getPlayers()) for (Card c : p.getCardsIn(ZoneType.Battlefield)) candidates.add(c);
        for (Card c : hc.getPlayer().getCardsIn(ZoneType.Graveyard)) candidates.add(c);
        for (Card c : candidates) {
            if (c.getName().toLowerCase(Locale.ROOT).contains(want)) {
                best = c;
                break;
            }
        }
        if (best == null) return "{\"error\":" + q("no card matching '" + name + "'") + "}";
        boolean handled = hc.getInputProxy().selectCard(best.getView(), null, TAP);
        return "{\"selected\":" + q(best.getName()) + ",\"handled\":" + handled + "}";
    }

    static String selectPlayer(String name) {
        Game g = duelGame();
        PlayerControllerHuman hc = g == null ? null : human(g);
        if (hc == null) return "{\"error\":\"no duel\"}";
        for (Player p : g.getPlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))) {
                hc.getInputProxy().selectPlayer(p.getView(), TAP);
                return "{\"selected\":" + q(p.getName()) + "}";
            }
        }
        return "{\"error\":" + q("no player matching '" + name + "'") + "}";
    }

    static String attackAll() {
        Game g = duelGame();
        PlayerControllerHuman hc = g == null ? null : human(g);
        if (hc == null) return "{\"error\":\"no duel\"}";
        hc.getInputProxy().alphaStrike();
        return "{\"ok\":true}";
    }

    // --- JSON helpers ---

    private static void field(StringBuilder sb, String name, String value) {
        sb.append(q(name)).append(':').append(q(value)).append(',');
    }

    private static String list(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q(items.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String r(float v) {
        return String.valueOf(Math.round(v));
    }

    /** Textratypist markup like [%85][GRAY] out of displayed text. */
    private static String stripMarkup(String s) {
        return s.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\{[^}]*\\}", "").trim();
    }

    static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append(' '); break;
                default:
                    if (c < 0x20) sb.append(' ');
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
