package {{ config.package_name() }};

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public enum FfiConverterString implements FfiConverter<String, MemorySegment> {
    INSTANCE;

    // Note: we don't inherit from FfiConverterRustBuffer, because we use a
    // special encoding when lowering/lifting.  We can use `RustBuffer.len` to
    // store our length and avoid writing it out to the buffer.
    @Override
    public String lift(MemorySegment value) {
        try {
            long len = RustBuffer.getLen(value);
            if (len == 0) {
                return "";
            }
            byte[] byteArr = new byte[(int) len];
            RustBuffer.asByteBuffer(value).get(byteArr);
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
    public MemorySegment lower(String value) {
        byte[] utf8Bytes = value.getBytes(StandardCharsets.UTF_8);
        long len = (long) utf8Bytes.length;
        MemorySegment rbuf = RustBuffer.alloc(len);
        if (len > 0) {
            RustBuffer.asWriteByteBuffer(rbuf).put(utf8Bytes);
        }
        RustBuffer.setLen(rbuf, len);
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


