package io.github.cygnus_engine;

/**
 * A mount point on a ship (socket). {@link WeaponData} ids are slotted via {@link #equippedWeaponId}.
 */
public class WeaponSlot {

    public enum SlotType {
        HARDPOINT,
        TURRET
    }

    /** Stable id within this ship (e.g. "slot_0"). */
    public String id;
    public float x;
    public float y;
    public SlotType type = SlotType.TURRET;
    
    /** Weapon definition id (JSON under each mod's {@code weapons} folder), or null if empty. */
    public String equippedWeaponId;

    public WeaponSlot() {}

    public WeaponSlot(String id, float x, float y, SlotType type) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.type = type;
    }
}
