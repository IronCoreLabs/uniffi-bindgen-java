package {{ config.package_name() }};

public enum FfiConverterFloat32Array implements FfiConverterRustBuffer<float[]> {
    INSTANCE;

    @Override
    public float[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        float[] arr = new float[len];
        buf.asFloatBuffer().get(arr);
        buf.position(buf.position() + len * 4);
        return arr;
    }

    @Override
    public long allocationSize(float[] value) {
        return 4L + (long) value.length * 4L;
    }

    @Override
    public void write(float[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.asFloatBuffer().put(value);
        buf.position(buf.position() + value.length * 4);
    }
}
