package io.github.cygnus_engine;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;


public class ShipData {
    /** Stable id / filename (e.g. "fighter"). */
    public String id;
    
    /** Display name for UI (defaults to id). */
    public String name;

    /** Path to PNG relative to local storage root (e.g. "mods/core/fighter.png"). */
    public String texturePath;

    public float speed;
    public float maneuverability;
    public float cargoSpace;

    /**
     * AI combat movement style: {@code FIGHTER} (dogfight + strafe dodge) or {@code FRIGATE} (orbit at
     * {@link #orbitCombatRadius}, no dodge strafe; relies on turrets and homing weapons).
     */
    public String combatProfile = "FIGHTER";
    
    /** Preferred standoff distance to enemy when using frigate orbit AI. */
    public float orbitCombatRadius = 220f;
    
    /** Hysteresis band so the ship does not oscillate at the orbit radius. */
    public float orbitCombatBand = 50f;
    
    /**
     * Hull yaw limit in degrees per second when this JSON is applied to an AI {@link SpaceShip}.
     * Use {@code 0} to keep engine default.
     */
    public float hullTurnDegPerSec = 0f;

    /**
     * Ship bounds in world units, relative to the ship's center (0,0).
     * Example: x=-16,y=-16,width=32,height=32
     */
    public Rectangle bounds = new Rectangle(-16, -16, 32, 32);

    public Vector2 centerOfMass = new Vector2();
    
    /** Mount points (sockets); each may reference a {@link WeaponData} id via {@link WeaponSlot#equippedWeaponId}. */
    public List<WeaponSlot> weaponSlots = new ArrayList<>();
    
    public List<Vector2> enginePositions = new ArrayList<Vector2>();
    
    /** Polygon-like collider vertices relative to ship center. */
    public List<Vector2> colliderVertices = new ArrayList<Vector2>();

    public void normalizeWeaponSlots() {
        if (weaponSlots == null) {
            weaponSlots = new ArrayList<>();
        }
        for (int i = 0; i < weaponSlots.size(); i++) {
            WeaponSlot s = weaponSlots.get(i);

            if (s == null) continue;

            if (s.id == null || s.id.isBlank()) {
                s.id = "slot_" + i;
            }

            if (s.type == null) {
                s.type = WeaponSlot.SlotType.TURRET;
            }
        }
    }

    public void normalizeCombatProfile() {
        if (combatProfile == null || combatProfile.isBlank()) {
            combatProfile = "FIGHTER";
        } else {
            combatProfile = combatProfile.trim().toUpperCase();
        }
        
        if (orbitCombatRadius <= 0f) {
            orbitCombatRadius = 220f;
        }
        if (orbitCombatBand <= 0f) {
            orbitCombatBand = 50f;
        }
    }
}
