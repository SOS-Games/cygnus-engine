package io.github.cygnus_engine;

/**
 * Weapon definition (plug) loaded from {@code mods/<mod>/weapons/*.json}.
 */
public class WeaponData {

    public String id;
    public String name;
    
    /** Free-form category for UI / future rules (e.g. BALLISTIC). */
    public String type = "GENERIC";
    
    /** Seconds between shots. */
    public float fireInterval = 0.35f;
    public float projectileSpeed = 280f;
    public float projectileLifetime = 2f;
    public float projectileRadius = 2.5f;
    
    /** Path to sprite relative to local storage (e.g. {@code mods/core/weapons/base.png}). */
    public String turretSprite;
    public boolean turretCompatible = true;
    public boolean hardpointCompatible = true;
    
    /** Turret traverse speed in degrees per second. */
    public float turnRateDegPerSec = 360f;
    
    /** If true, projectile steers toward {@link SpaceShip}'s combat target each tick (see {@link #homingTurnRateDegPerSec}). */
    public boolean homing = false;
    /** Homing turn rate in degrees per second when {@link #homing} is true. */
    public float homingTurnRateDegPerSec = 120f;
    /** If true, projectiles damage {@link GameObject.Type#ASTEROID} bodies instead of ships. */
    public boolean minesAsteroids = false;
    /** Damage applied to an asteroid per hit when {@link #minesAsteroids} is true. */
    public float miningDamage = 12f;

    public boolean canEquipOn(WeaponSlot.SlotType slotType) {
        return switch (slotType) {
            case TURRET -> turretCompatible;
            case HARDPOINT -> hardpointCompatible;
        };
    }
}
