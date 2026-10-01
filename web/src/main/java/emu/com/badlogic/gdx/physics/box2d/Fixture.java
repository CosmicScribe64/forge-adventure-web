package emu.com.badlogic.gdx.physics.box2d;

import com.badlogic.gdx.math.Vector2;

public class Fixture {
    private final Body body;
    private final PolygonShape shape;
    private Object userData;
    // World-space bounds, fixed because Forge's bodies are static.
    final float minX, minY, maxX, maxY;

    Fixture(Body body, PolygonShape source) {
        this.body = body;
        this.shape = new PolygonShape();
        shape.setAsBox(source.halfWidth, source.halfHeight, source.center, 0);
        float cx = body.getPosition().x + source.center.x;
        float cy = body.getPosition().y + source.center.y;
        minX = cx - source.halfWidth;
        maxX = cx + source.halfWidth;
        minY = cy - source.halfHeight;
        maxY = cy + source.halfHeight;
    }

    public Body getBody() { return body; }
    public Shape getShape() { return shape; }
    public Shape.Type getType() { return Shape.Type.Polygon; }
    public Object getUserData() { return userData; }
    public void setUserData(Object data) { userData = data; }
    public boolean isSensor() { return false; }

    public boolean testPoint(float x, float y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public boolean testPoint(Vector2 p) {
        return testPoint(p.x, p.y);
    }

    /**
     * Segment/box intersection (slab method). Returns the entry fraction along p1->p2 in
     * [0, 1], or -1 when the segment misses; writes the surface normal into {@code normal}.
     */
    float rayCast(float x1, float y1, float x2, float y2, Vector2 normal) {
        float dx = x2 - x1, dy = y2 - y1;
        float tMin = 0f, tMax = 1f;
        float nx = 0, ny = 0;
        if (Math.abs(dx) < 1e-9f) {
            if (x1 < minX || x1 > maxX) return -1;
        } else {
            float t1 = (minX - x1) / dx, t2 = (maxX - x1) / dx;
            float s = -1;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; s = 1; }
            if (t1 > tMin) { tMin = t1; nx = s; ny = 0; }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }
        if (Math.abs(dy) < 1e-9f) {
            if (y1 < minY || y1 > maxY) return -1;
        } else {
            float t1 = (minY - y1) / dy, t2 = (maxY - y1) / dy;
            float s = -1;
            if (t1 > t2) { float t = t1; t1 = t2; t2 = t; s = 1; }
            if (t1 > tMin) { tMin = t1; nx = 0; ny = s; }
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return -1;
        }
        normal.set(nx, ny);
        return tMin;
    }
}
