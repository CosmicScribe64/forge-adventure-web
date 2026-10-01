package forge.adventure.stage;

import com.badlogic.gdx.scenes.scene2d.Stage;

/** Lets forgeweb.test.WebTest reach a GameStage's dialog stage (a protected field). */
public final class WebTestStageAccess {
    private WebTestStageAccess() {
    }

    /** The HUD's world size and its elements' bounds, as JSON (harness command "layout"). */
    public static String hudLayout() {
        GameHUD hud = GameHUD.getInstance();
        StringBuilder sb = new StringBuilder("{\"world\":[");
        sb.append(hud.getViewport().getWorldWidth()).append(',').append(hud.getViewport().getWorldHeight());
        sb.append("],\"view\":[").append(forge.adventure.scene.Scene.getViewWidth()).append(',')
                .append(forge.adventure.scene.Scene.getViewHeight()).append("],\"elements\":[");
        boolean first = true;
        for (com.badlogic.gdx.scenes.scene2d.Actor a : hud.ui.getChildren()) {
            if (!first) sb.append(',');
            first = false;
            sb.append("[\"").append(a.getName()).append("\",").append(Math.round(a.getX())).append(',')
                    .append(Math.round(a.getY())).append(',').append(Math.round(a.getWidth())).append(',')
                    .append(Math.round(a.getHeight())).append(']');
        }
        return sb.append("]}").toString();
    }

    /** The stage dialogs are shown on (NPC talk, quest and town messages), or null. */
    public static Stage dialogStage(GameStage stage) {
        return stage.dialogStage;
    }
}
