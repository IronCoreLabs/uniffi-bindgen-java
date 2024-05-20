package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum FfiConverterFloat implements FfiConverter<Float, Float>{
  INSTANCE;

    @Override
    public Float lift(Float value) {
        return value;
    }

    @Override
    public Float read(ByteBuffer buf) {
        return buf.getFloat();
    }

    @Override
    public Float lower(Float value) {
        return value;
    }

    @Override
    public long allocationSize(Float value) {
        return 4L;
    }

    @Override
    public void write(Float value, ByteBuffer buf) {
        buf.putFloat(value);
    }
}
