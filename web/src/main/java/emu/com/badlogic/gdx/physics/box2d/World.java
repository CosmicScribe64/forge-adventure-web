package emu.com.badlogic.gdx.physics.box2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/** No simulation: bodies are static; only queries are supported. */
public class World implements Disposable {
    final Array<Body> bodies = new Array<>();
    final Array<Fixture> fixtures = new Array<>();
    private final Vector2 gravity = new Vector2();
    private final Vector2 point = new Vector2();
    private final Vector2 normal = new Vector2();

    public World(Vector2 gravity, boolean doSleep) {
        this.gravity.set(gravity);
    }

    public Body createBody(BodyDef def) {
        Body b = new Body(this, def);
        bodies.add(b);
        return b;
    }

    public void destroyBody(Body body) {
        for (Fixture f : body.getFixtureList()) {
            fixtures.removeValue(f, true);
        }
        bodies.removeValue(body, true);
    }

    public void getBodies(Array<Body> out) {
        out.clear();
        out.addAll(bodies);
    }

    public void getFixtures(Array<Fixture> out) {
        out.clear();
        out.addAll(fixtures);
    }

    public int getBodyCount() { return bodies.size; }
    public int getFixtureCount() { return fixtures.size; }
    public Vector2 getGravity() { return gravity; }
    public void setGravity(Vector2 g) { gravity.set(g); }
    public void step(float timeStep, int velocityIterations, int positionIterations) { }

    public void rayCast(RayCastCallback callback, Vector2 p1, Vector2 p2) {
        rayCast(callback, p1.x, p1.y, p2.x, p2.y);
    }

    /** Box2D semantics: callback returns -1 to ignore, 0 to stop, else the new max fraction. */
    public void rayCast(RayCastCallback callback, float x1, float y1, float x2, float y2) {
        float maxFraction = 1f;
        for (int i = 0; i < fixtures.size; i++) {
            Fixture f = fixtures.get(i);
            float t = f.rayCast(x1, y1, x2, y2, normal);
            if (t < 0 || t > maxFraction) continue;
            point.set(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t);
            float r = callback.reportRayFixture(f, point, normal, t);
            if (r == 0) return;
            if (r > 0) maxFraction = Math.min(maxFraction, r);
        }
    }

    @Override
    public void dispose() {
        bodies.clear();
        fixtures.clear();
    }
}
