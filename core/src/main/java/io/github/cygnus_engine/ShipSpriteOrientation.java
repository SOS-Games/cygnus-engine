package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

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

    /** Rotation for the mod editor preview (fixed nose-up, same as gameplay heading {@link #EDITOR_REFERENCE_GAME_ANGLE}). */
    public static float editorSpriteRotation() {
        return gameAngleToSpriteRotation(EDITOR_REFERENCE_GAME_ANGLE);
    }

    /**
     * Rotation for transforming ship-local points (mounts, colliders) to world space.
     * Must match hull sprite rotation so authored +Y forward aligns with the drawn hull.
     */
    public static float gameAngleToLocalTransformRotation(float gameAngleDeg) {
        return gameAngleToSpriteRotation(gameAngleDeg);
    }

    public static void applyHullLayout(Sprite sprite, Rectangle bounds, float comX, float comY, int texWidth, int texHeight) {
        float w = texWidth;
        float h = texHeight;
        if (bounds != null && bounds.width > 0f && bounds.height > 0f) {
            w = bounds.width;
            h = bounds.height;
        }
        sprite.setSize(w, h);
        sprite.setOrigin(sprite.getWidth() * 0.5f + comX, sprite.getHeight() * 0.5f + comY);
    }

    /** Scale from hull texture pixels to world units (bounds). */
    public static void computeHullPixelScale(Rectangle bounds, int texWidth, int texHeight, Vector2 out) {
        if (bounds != null && bounds.width > 0f && bounds.height > 0f && texWidth > 0 && texHeight > 0) {
            out.set(bounds.width / texWidth, bounds.height / texHeight);
        } else {
            out.set(1f, 1f);
        }
    }

    public static void applyWeaponPixelScale(Sprite weaponSprite, float scaleX, float scaleY) {
        weaponSprite.setSize(weaponSprite.getTexture().getWidth() * scaleX, weaponSprite.getTexture().getHeight() * scaleY);
        weaponSprite.setOriginCenter();
    }

    public static void applyWeaponPixelScale(Sprite weaponSprite, Rectangle hullBounds, int hullTexWidth, int hullTexHeight) {
        Vector2 scale = new Vector2();
        computeHullPixelScale(hullBounds, hullTexWidth, hullTexHeight, scale);
        applyWeaponPixelScale(weaponSprite, scale.x, scale.y);
    }
}
