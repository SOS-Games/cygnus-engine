package io.github.cygnus_engine;

import java.util.ArrayList;
import java.util.List;

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
     * AI combat movement style: {@code FIGHTER} (closer orbit, frequent dodge, hull aims at lead)
     * or {@code FRIGATE} (wider orbit, slower dodge, tangential hull; turrets/homing fire).
     */
    public String combatProfile = "FIGHTER";
    
    /**
     * Hull yaw limit in degrees per second when this JSON is applied to an AI {@link SpaceShip}.
     * Use {@code 0} to keep engine default.
     */
    public float hullTurnDegPerSec = 0f;

    public Vector2 centerOfMass = new Vector2();
    
    /** Mount points (sockets); each may reference a {@link WeaponData} id via {@link WeaponSlot#equippedWeaponId}. */
    public List<WeaponSlot> weaponSlots = new ArrayList<>();
    
    public List<Vector2> enginePositions = new ArrayList<Vector2>();

    /** Overlapping hit circles in hull-local space for projectile collision. */
    public List<ShipColliderCircle> colliders = new ArrayList<>();

    /** Large click target in hull-local space; required and used only for mouse picking. */
    public ShipColliderCircle outerBounds = new ShipColliderCircle();

    public void normalizeColliders() {
        if (colliders == null) {
            colliders = new ArrayList<>();
        }
        colliders.removeIf(c -> c == null || !(c.radius > 0f));
    }

    public void normalizeOuterBounds() {
        if (outerBounds == null) {
            outerBounds = new ShipColliderCircle();
        }
        if (outerBounds.radius > 0f) {
            return;
        }
        float maxReach = 0f;
        if (colliders != null) {
            for (ShipColliderCircle c : colliders) {
                if (c == null || !(c.radius > 0f)) continue;
                float reach = (float) Math.sqrt(c.x * c.x + c.y * c.y) + c.radius;
                maxReach = Math.max(maxReach, reach);
            }
        }
        if (maxReach > 0f) {
            outerBounds.set(0f, 0f, maxReach + 6f);
        } else {
            outerBounds.set(0f, 0f, 24f);
        }
    }

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
    }
}
