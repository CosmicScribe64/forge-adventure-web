package forge.adventure.scene;

import com.badlogic.gdx.scenes.scene2d.Stage;

/** Lets forgeweb.test.WebTest reach a scene's stage (package-private/protected fields). */
public final class WebTestAccess {
    private WebTestAccess() {
    }

    /** The scene's scene2d stage: a GameStage (WorldStage/MapStage) for HudScenes, else the UI stage. */
    public static Stage stageOf(Scene scene) {
        if (scene instanceof HudScene) return ((HudScene) scene).stage;
        if (scene instanceof UIScene) return ((UIScene) scene).stage;
        return null;
    }

    /** Stages to look at for UI, topmost first: a GameStage's dialog stage, then the scene's stage. */
    public static java.util.List<Stage> uiStagesOf(Scene scene) {
        java.util.List<Stage> stages = new java.util.ArrayList<>();
        Stage main = stageOf(scene);
        if (main instanceof forge.adventure.stage.GameStage) {
            addDialogStage(stages, (forge.adventure.stage.GameStage) main);
            // Quest dialogs go through MapStage.getInstance() even on the world map (and while
            // one is open the world map ignores towns and enemies).
            addDialogStage(stages, forge.adventure.stage.MapStage.getInstance());
        }
        if (main != null) stages.add(main);
        return stages;
    }

    private static void addDialogStage(java.util.List<Stage> stages, forge.adventure.stage.GameStage stage) {
        Stage dialog = forge.adventure.stage.WebTestStageAccess.dialogStage(stage);
        if (dialog != null && dialog.getRoot().hasChildren() && !stages.contains(dialog)) stages.add(dialog);
    }
}
