package emu.com.badlogic.gdx.physics.box2d;

import com.badlogic.gdx.math.Vector2;

public interface RayCastCallback {
    float reportRayFixture(Fixture fixture, Vector2 point, Vector2 normal, float fraction);
}
