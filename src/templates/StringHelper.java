package {{ config.package_name() }};

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public enum FfiConverterString implements FfiConverter<String, RustBuffer.ByValue> {
    INSTANCE;

    // Note: we don't inherit from FfiConverterRustBuffer, because we use a
    // special encoding when lowering/lifting.  We can use `RustBuffer.len` to
    // store our length and avoid writing it out to the buffer.
    @Override
    public String lift(RustBuffer.ByValue value) {
        try {
            byte[] byteArr = new byte[(int) value.len];
            value.asByteBuffer().get(byteArr);
            return new String(byteArr, StandardCharsets.UTF_8);
        } finally {
            RustBuffer.free(value);
        }
    }

    @Override
    public String read(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] byteArr = new byte[len];
        buf.get(byteArr);
        return new String(byteArr, StandardCharsets.UTF_8);
    }

    @Override
    public RustBuffer.ByValue lower(String value) {
        byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        RustBuffer.ByValue rbuf = RustBuffer.alloc((long) utf8Bytes.length);
        rbuf.asByteBuffer().put(utf8Bytes);
        return rbuf;
    }

    @Override
    public long allocationSize(String value) {
        long sizeForLength = 4L;
        long sizeForString = (long) value.getBytes(StandardCharsets.UTF_8).length;
        return sizeForLength + sizeForString;
    }

    @Override
    public void write(String value, ByteBuffer buf) {
        byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.putInt(utf8Bytes.length);
        buf.put(utf8Bytes);
    }
}


