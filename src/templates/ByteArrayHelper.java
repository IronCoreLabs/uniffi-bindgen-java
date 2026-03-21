package {{ config.package_name() }};

public enum FfiConverterByteArray implements FfiConverterRustBuffer<byte[]>{
  INSTANCE;

    @Override
    public byte[] read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        byte[] byteArr = new byte[len];
        buf.get(byteArr);
        return byteArr;
    }

    @Override
    public long allocationSize(byte[] value) {
        return 4L + (long)value.length;
    }

    @Override
    public void write(byte[] value, java.nio.ByteBuffer buf) {
        buf.putInt(value.length);
        buf.put(value);
    }
}
