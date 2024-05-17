package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum FfiConverterByte implements FfiConverter<Byte, Byte>{
  INSTANCE;

    @Override
    public Byte lift(Byte value) {
        return value;
    }

    @Override
    public Byte read(ByteBuffer buf) {
        return buf.get();
    }

    @Override
    public Byte lower(Byte value) {
        return value;
    }

    @Override
    public long allocationSize(Byte value) {
        return 1L;
    }

    @Override
    public void write(Byte value, ByteBuffer buf) {
        buf.put(value);
    }
}
