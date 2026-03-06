package io.github.cygnus_engine;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.math.Vector2;


public class ShipData {
    public String name;
    public float speed;
    public float maneuverability;
    public Vector2 centerOfMass = new Vector2();
    public List<Vector2> weaponSlots = new ArrayList<Vector2>();
    public List<Vector2> enginePositions = new ArrayList<Vector2>();
}
