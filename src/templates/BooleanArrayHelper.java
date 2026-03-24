package {{ config.package_name() }};

public enum FfiConverterBooleanArray implements FfiConverterRustBuffer<boolean[]> {
    INSTANCE;

    @Override
    public boolean[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        boolean[] arr = new boolean[len];
        for (int i = 0; i < len; i++) {
            arr[i] = buf.get() != 0;
        }
        return arr;
    }

    @Override
    public long allocationSize(boolean[] value) {
        return 4L + (long) value.length;
    }

    @Override
    public void write(boolean[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        for (boolean b : value) {
            buf.put(b ? (byte) 1 : (byte) 0);
        }
    }
}
