package {{ config.package_name() }};

public enum FfiConverterInt64Array implements FfiConverterRustBuffer<long[]> {
    INSTANCE;

    @Override
    public long[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        long[] arr = new long[len];
        buf.asLongBuffer().get(arr);
        buf.position(buf.position() + len * 8);
        return arr;
    }

    @Override
    public long allocationSize(long[] value) {
        return 4L + (long) value.length * 8L;
    }

    @Override
    public void write(long[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.asLongBuffer().put(value);
        buf.position(buf.position() + value.length * 8);
    }
}
