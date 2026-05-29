package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.g2d.Sprite;

/**
 * Hull and weapon PNGs are authored with the nose toward +Y (top of the image).
 * Gameplay angles use {@link CustomMathUtils#getAngleBetweenPoints} where 0° points along +X.
 */
public final class ShipSpriteOrientation {
    /** Add to a gameplay angle to get the {@link Sprite#setRotation(float)} value for PNG-up art. */
    public static final float PNG_UP_TO_GAME_ANGLE_OFFSET = -90f;

    /** Gameplay heading used by the ship editor (nose toward screen +Y). */
    public static final float EDITOR_REFERENCE_GAME_ANGLE = 90f;

    private ShipSpriteOrientation() {}

    public static float gameAngleToSpriteRotation(float gameAngleDeg) {
        return gameAngleDeg + PNG_UP_TO_GAME_ANGLE_OFFSET;
    }

    /** Rotation for the mod editor preview (fixed nose-up). */
    public static float editorSpriteRotation() {
        return gameAngleToSpriteRotation(EDITOR_REFERENCE_GAME_ANGLE);
    }

    /** Rotation for transforming ship-local points (mounts, colliders) to world space. */
    public static float gameAngleToLocalTransformRotation(float gameAngleDeg) {
        return gameAngleToSpriteRotation(gameAngleDeg);
    }

    /** Hull sprites use texture pixels as world units (1 texel = 1 world unit). */
    public static void applyHullLayout(Sprite sprite, float comX, float comY, int texWidth, int texHeight) {
        sprite.setSize(texWidth, texHeight);
        sprite.setOrigin(sprite.getWidth() * 0.5f + comX, sprite.getHeight() * 0.5f + comY);
    }

    public static void applyWeaponLayout(Sprite weaponSprite) {
        weaponSprite.setOriginCenter();
    }

    public static float hullRadiusFromTexture(int texWidth, int texHeight) {
        if (texWidth <= 0 || texHeight <= 0) {
            return 18f;
        }
        return Math.max(texWidth, texHeight) * 0.5f;
    }
}
