package {{ config.package_name() }};

public enum FfiConverterInt32Array implements FfiConverterRustBuffer<int[]> {
    INSTANCE;

    @Override
    public int[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        int[] arr = new int[len];
        buf.asIntBuffer().get(arr);
        buf.position(buf.position() + len * 4);
        return arr;
    }

    @Override
    public long allocationSize(int[] value) {
        return 4L + (long) value.length * 4L;
    }

    @Override
    public void write(int[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.asIntBuffer().put(value);
        buf.position(buf.position() + value.length * 4);
    }
}
