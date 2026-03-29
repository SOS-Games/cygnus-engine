package io.github.cygnus_engine;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

public final class UiWindowUtils {
    private UiWindowUtils() {}

    public static void createAndCenterWindow(Window window, Stage stage) {
        createAndCenterWindow(window, stage, 0f);
    }

    public static void createAndCenterWindow(Window window, Stage stage, float minPadding) {
        window.setMovable(true);
        window.pack();
        float x = Math.max(minPadding, stage.getWidth() / 2f - window.getWidth() / 2f);
        float y = Math.max(minPadding, stage.getHeight() / 2f - window.getHeight() / 2f);
        window.setPosition(MathUtils.roundPositive(x), MathUtils.roundPositive(y));
        stage.addActor(window);
    }
}
