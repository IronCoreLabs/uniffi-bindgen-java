package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

/**
 * This is a helper for safely working with byte buffers returned from the Rust code.
 * A rust-owned buffer is represented by its capacity, its current length, and a
 * pointer to the underlying data.
 */
@Structure.FieldOrder({ "capacity", "len", "data" })
public class RustBuffer extends Structure {
    public long capacity;
    public long len;
    public Pointer data;

    public static class ByValue extends RustBuffer implements Structure.ByValue {}
    public static class ByReference extends RustBuffer implements Structure.ByReference {}

    void setValue(RustBuffer other) {
        this.capacity = other.capacity;
        this.len = other.len;
        this.data = other.data;
    }

    public static RustBuffer.ByValue alloc(long size) {
        RustBuffer.ByValue buffer = UniffiHelpers.uniffiRustCall((UniffiRustCallStatus status) -> {
            return (RustBuffer.ByValue) UniffiLib.INSTANCE.{{ ci.ffi_rustbuffer_alloc().name() }}(size, status);
        });
        if (buffer.data == null) {
            throw new RuntimeException("RustBuffer.alloc() returned null data pointer (size=" + size + ")");
        }
        return buffer;
    }

    public static void free(RustBuffer.ByValue buffer) {
        UniffiHelpers.uniffiRustCall((status) -> {
            UniffiLib.INSTANCE.{{ ci.ffi_rustbuffer_free().name() }}(buffer, status);
            return null;
        });
    }

    public java.nio.ByteBuffer asByteBuffer() {
        if (this.data != null) {
            java.nio.ByteBuffer byteBuffer = this.data.getByteBuffer(0, this.len);
            byteBuffer.order(java.nio.ByteOrder.BIG_ENDIAN);
            return byteBuffer;
        }
        return null;
    }
}

package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;
/**
 * The equivalent of the `*mut RustBuffer` type.
 * Required for callbacks taking in an out pointer.
 *
 * Size is the sum of all values in the struct.
 */
public class RustBufferByReference extends Structure implements Structure.ByReference {
    public RustBufferByReference() {
        super(16);
    }

    /**
     * Set the pointed-to `RustBuffer` to the given value.
     */
    public void setValue(RustBuffer.ByValue value) {
        // NOTE: The offsets are as they are in the C-like struct.
        Pointer pointer = this.getPointer();
        pointer.setInt(0, (int) value.capacity);
        pointer.setInt(4, (int) value.len);
        pointer.setPointer(8, value.data);
    }

    /**
     * Get a `RustBuffer.ByValue` from this reference.
     */
    public RustBuffer.ByValue getValue() {
        Pointer pointer = this.getPointer();
        RustBuffer.ByValue value = new RustBuffer.ByValue();
        value.writeField("capacity", pointer.getLong(0));
        value.writeField("len", pointer.getLong(8));
        value.writeField("data", pointer.getLong(16));

        return value;
    }
}

package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

// This is a helper for safely passing byte references into the rust code.
// It's not actually used at the moment, because there aren't many things that you
// can take a direct pointer to in the JVM, and if we're going to copy something
// then we might as well copy it into a `RustBuffer`. But it's here for API
// completeness.
@Structure.FieldOrder({ "len", "data" })
public class ForeignBytes extends Structure {
    public int len;
    public Pointer data;

    public static class ByValue extends ForeignBytes implements Structure.ByValue {}
}
