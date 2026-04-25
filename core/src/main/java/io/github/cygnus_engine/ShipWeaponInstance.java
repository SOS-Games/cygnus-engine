package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Vector2;

/**
 * Runtime weapon mounted in a {@link WeaponSlot}.
 */
public class ShipWeaponInstance {
    public final WeaponSlot slot;

    public WeaponData data;
    
    /** World-space aim angle in degrees (LibGDX convention, same as ship rotation). */
    public float aimAngleDeg;

    public float fireCooldown;

    public Sprite sprite;
    
    /** Non-owning reference; textures owned by cache in {@link GameWorld}. */
    public Texture textureRef;
    
    /** Cached mount world position; refreshed from ship transform each frame. */
    public final Vector2 worldPosCache = new Vector2();

    public ShipWeaponInstance(WeaponSlot slot, WeaponData data) {
        this.slot = slot;
        this.data = data;
        this.aimAngleDeg = 0f;
    }

    public void updateWorldPosition(Affine2 shipTransform) {
        worldPosCache.set(slot.x, slot.y);
        shipTransform.applyTo(worldPosCache);
    }
}
