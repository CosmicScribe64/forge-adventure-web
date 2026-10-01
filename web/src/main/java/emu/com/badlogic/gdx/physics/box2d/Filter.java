package emu.com.badlogic.gdx.physics.box2d;

public class Filter {
    public short categoryBits = 0x0001;
    public short maskBits = -1;
    public short groupIndex = 0;

    public void set(Filter f) {
        categoryBits = f.categoryBits;
        maskBits = f.maskBits;
        groupIndex = f.groupIndex;
    }
}
