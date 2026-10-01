package emu.com.badlogic.gdx.physics.box2d;

import com.badlogic.gdx.math.Vector2;

/** Axis-aligned boxes only (setAsBox without rotation), which is all Forge creates. */
public class PolygonShape extends Shape {
    float halfWidth;
    float halfHeight;
    final Vector2 center = new Vector2();

    public PolygonShape() { }

    @Override
    public Type getType() { return Type.Polygon; }

    public void setAsBox(float hx, float hy) {
        halfWidth = hx;
        halfHeight = hy;
        center.setZero();
    }

    public void setAsBox(float hx, float hy, Vector2 c, float angle) {
        if (angle != 0) throw new UnsupportedOperationException("Rotated boxes are not supported on web");
        halfWidth = hx;
        halfHeight = hy;
        center.set(c);
    }

    public int getVertexCount() { return 4; }
}
