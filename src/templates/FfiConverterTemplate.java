package {{ config.package_name() }};

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// The FfiConverter interface handles converter types to and from the FFI
//
// All implementing objects should be public to support external types.  When a
// type is external we need to import it's FfiConverter.
public interface FfiConverter<JavaType, FfiType> {
    // Convert an FFI type to a Java type
    JavaType lift(FfiType value);

    // Convert an Java type to an FFI type
    FfiType lower(JavaType value);

    // Read a Java type from a `ByteBuffer`
    JavaType read(ByteBuffer buf);

    // Calculate bytes to allocate when creating a `RustBuffer`
    //
    // This must return at least as many bytes as the write() function will
    // write. It can return more bytes than needed, for example when writing
    // Strings we can't know the exact bytes needed until we the UTF-8
    // encoding, so we pessimistically allocate the largest size possible (3
    // bytes per codepoint).  Allocating extra bytes is not really a big deal
    // because the `RustBuffer` is short-lived.
    long allocationSize(JavaType value);

    // Write a Java type to a `ByteBuffer`
    void write(JavaType value, ByteBuffer buf);

    // Lower a value into a `RustBuffer`
    //
    // This method lowers a value into a `RustBuffer` rather than the normal
    // FfiType.  It's used by the callback interface code.  Callback interface
    // returns are always serialized into a `RustBuffer` regardless of their
    // normal FFI type.
    default MemorySegment lowerIntoRustBuffer(JavaType value) {
        long size = allocationSize(value);
        MemorySegment rbuf = RustBuffer.alloc(size);
        try {
            if (size > 0) {
                ByteBuffer bbuf = RustBuffer.asWriteByteBuffer(rbuf);
                write(value, bbuf);
                RustBuffer.setLen(rbuf, (long)bbuf.position());
            }
            return rbuf;
        } catch (Throwable e) {
            RustBuffer.free(rbuf);
            throw e;
        }
    }

    // Lift a value from a `RustBuffer`.
    //
    // This here mostly because of the symmetry with `lowerIntoRustBuffer()`.
    // It's currently only used by the `FfiConverterRustBuffer` class below.
    default JavaType liftFromRustBuffer(MemorySegment rbuf) {
        try {
            ByteBuffer byteBuf = RustBuffer.asByteBuffer(rbuf);
            if (byteBuf == null) {
                // Zero-length buffer; wrap an empty ByteBuffer for read()
                byteBuf = ByteBuffer.allocate(0).order(ByteOrder.BIG_ENDIAN);
            }
            JavaType item = read(byteBuf);
            if (byteBuf.hasRemaining()) {
                throw new RuntimeException("junk remaining in buffer after lifting, something is very wrong!!");
            }
            return item;
        } finally {
            RustBuffer.free(rbuf);
        }
    }
}

package {{ config.package_name() }};

import java.lang.foreign.MemorySegment;

// FfiConverter that uses `RustBuffer` (MemorySegment) as the FfiType
public interface FfiConverterRustBuffer<JavaType> extends FfiConverter<JavaType, MemorySegment> {
    @Override
    default JavaType lift(MemorySegment value) {
        return liftFromRustBuffer(value);
    }
    @Override
    default MemorySegment lower(JavaType value) {
        return lowerIntoRustBuffer(value);
    }
}
