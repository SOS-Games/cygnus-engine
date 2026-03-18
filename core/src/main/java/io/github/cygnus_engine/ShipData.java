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
     * Ship bounds in world units, relative to the ship's center (0,0).
     * Example: x=-16,y=-16,width=32,height=32
     */
    public Rectangle bounds = new Rectangle(-16, -16, 32, 32);

    public Vector2 centerOfMass = new Vector2();
    public List<Vector2> weaponSlots = new ArrayList<Vector2>();
    public List<Vector2> enginePositions = new ArrayList<Vector2>();
}
