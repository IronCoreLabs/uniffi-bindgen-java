package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

/**
 * This is a helper for safely working with byte buffers returned from the Rust code.
 * A rust-owned buffer is represented by its capacity, its current length, and a
 * pointer to the underlying data.
 */
@Structure.FieldOrder("capacity", "len", "data")
public class RustBuffer extends Structure {
    public int capacity;
    public int len;
    public Pointer data;

    public static class ByValue extends RustBuffer implements Structure.ByValue {}
    public static class ByReference extends RustBuffer implements Structure.ByReference {}

    public static RustBuffer alloc(int size) {
        RustBuffer.ByValue buffer;

        UniffiHelpers.uniffiRustCall((status) -> {
            buffer = (RustBuffer.ByValue) UniffiLib.INSTANCE.{{ ci.ffi_rustbuffer_alloc().name() }}((long) size, status);
        });
        if (buffer.data == null) {
            throw new RuntimeException("RustBuffer.alloc() returned null data pointer (size=" + size + ")");
        }
        return buffer;
    }

    public static void free(RustBuffer.ByValue buffer) {
        UniffiHelpers.uniffiRustCall((status) -> {
            UniffiLib.INSTANCE.{{ ci.ffi_rustbuffer_free().name() }}(buffer, status);
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
public class RustBufferByReference extends Structure.ByReference {
    public RustBufferByReference() {
        super(16);
    }

    /**
     * Set the pointed-to `RustBuffer` to the given value.
     */
    public void setValue(RustBuffer.ByValue value) {
        // NOTE: The offsets are as they are in the C-like struct.
        Pointer pointer = this.getPointer();
        pointer.setInt(0, value.capacity);
        pointer.setInt(4, value.len);
        pointer.setPointer(8, value.data);
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
@Structure.FieldOrder("len", "data")
public class ForeignBytes extends Structure {
    public int len;
    public Pointer data;

    public static class ByValue extends ForeignBytes implements Structure.ByValue {}
}
