package emu.com.badlogic.gdx.physics.box2d;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class Body {
    private final World world;
    private final Vector2 position = new Vector2();
    private final Array<Fixture> fixtures = new Array<>();
    private Object userData;

    Body(World world, BodyDef def) {
        this.world = world;
        position.set(def.position);
    }

    public Fixture createFixture(FixtureDef def) {
        return createFixture(def.shape, def.density);
    }

    public Fixture createFixture(Shape shape, float density) {
        if (!(shape instanceof PolygonShape)) {
            throw new UnsupportedOperationException("Only box shapes are supported on web");
        }
        Fixture f = new Fixture(this, (PolygonShape) shape);
        fixtures.add(f);
        world.fixtures.add(f);
        return f;
    }

    public void destroyFixture(Fixture f) {
        fixtures.removeValue(f, true);
        world.fixtures.removeValue(f, true);
    }

    public Array<Fixture> getFixtureList() { return fixtures; }
    public Vector2 getPosition() { return position; }
    public Vector2 getWorldCenter() { return position; }
    public float getAngle() { return 0; }
    public World getWorld() { return world; }
    public Object getUserData() { return userData; }
    public void setUserData(Object data) { userData = data; }
}
