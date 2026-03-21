package {{ config.package_name() }};

public enum FfiConverterFloat64Array implements FfiConverterRustBuffer<double[]> {
    INSTANCE;

    @Override
    public double[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        double[] arr = new double[len];
        buf.asDoubleBuffer().get(arr);
        buf.position(buf.position() + len * 8);
        return arr;
    }

    @Override
    public long allocationSize(double[] value) {
        return 4L + (long) value.length * 8L;
    }

    @Override
    public void write(double[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.asDoubleBuffer().put(value);
        buf.position(buf.position() + value.length * 8);
    }
}
