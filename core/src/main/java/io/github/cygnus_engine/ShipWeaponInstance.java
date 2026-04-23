package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Runtime weapon mounted in a {@link WeaponSlot}.
 */
public class ShipWeaponInstance {
    public final WeaponSlot slot;
    public WeaponData data;
    /** World-space aim angle in degrees (LibGDX convention, same as ship rotation). */
    public float aimAngleDeg;
    public float fireCooldown;

    public TextureRegion region;
    /** Non-owning reference; textures owned by cache in {@link GameWorld}. */
    public Texture textureRef;

    public ShipWeaponInstance(WeaponSlot slot, WeaponData data) {
        this.slot = slot;
        this.data = data;
        this.aimAngleDeg = 0f;
    }
}
