package {{ config.package_name() }};

public enum FfiConverterInt16Array implements FfiConverterRustBuffer<short[]> {
    INSTANCE;

    @Override
    public short[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        short[] arr = new short[len];
        buf.asShortBuffer().get(arr);
        buf.position(buf.position() + len * 2);
        return arr;
    }

    @Override
    public long allocationSize(short[] value) {
        return 4L + (long) value.length * 2L;
    }

    @Override
    public void write(short[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.asShortBuffer().put(value);
        buf.position(buf.position() + value.length * 2);
    }
}
