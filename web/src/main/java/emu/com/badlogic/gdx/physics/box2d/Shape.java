package emu.com.badlogic.gdx.physics.box2d;

/**
 * Web replacement for libGDX's native Box2D, covering what Forge Adventure uses:
 * static axis-aligned boxes queried with rayCast/testPoint for pathfinding.
 * gdx-teavm substitutes emu.com.badlogic.gdx.X for com.badlogic.gdx.X.
 */
public abstract class Shape {
    public enum Type { Circle, Edge, Polygon, Chain }

    protected float radius = 0.01f;

    public abstract Type getType();

    public float getRadius() { return radius; }
    public void setRadius(float r) { radius = r; }
    public int getChildCount() { return 1; }
    public void dispose() { }
}
