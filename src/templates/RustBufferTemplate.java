package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

/**
 * This is a helper for safely working with byte buffers returned from the Rust code.
 * A rust-owned buffer is represented by its capacity, its current length, and a
 * pointer to the underlying data.
 *
 * In FFM, RustBuffer is a utility class with a StructLayout and static accessors
 * rather than a JNA Structure subclass.
 */
public final class RustBuffer {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("capacity"),
        ValueLayout.JAVA_LONG.withName("len"),
        ValueLayout.ADDRESS.withName("data")
    );

    private static final long OFFSET_CAPACITY = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("capacity"));
    private static final long OFFSET_LEN = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("len"));
    private static final long OFFSET_DATA = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("data"));

    private RustBuffer() {}

    public static long getCapacity(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_CAPACITY);
    }

    public static void setCapacity(MemorySegment seg, long value) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_CAPACITY, value);
    }

    public static long getLen(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_LEN);
    }

    public static void setLen(MemorySegment seg, long value) {
        seg.set(ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_LEN, value);
    }

    public static MemorySegment getData(MemorySegment seg) {
        return seg.get(ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA);
    }

    public static void setData(MemorySegment seg, MemorySegment value) {
        seg.set(ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA, value);
    }

    public static MemorySegment allocate(Arena arena) {
        return arena.allocate(LAYOUT);
    }

    public static MemorySegment alloc(long size) {
        MemorySegment buffer = UniffiHelpers.uniffiRustCall((_arena, status) -> {
            return UniffiLib.{{ ci.ffi_rustbuffer_alloc().name() }}(_arena, size, status);
        });
        MemorySegment data = getData(buffer);
        if (data.equals(MemorySegment.NULL)) {
            throw new RuntimeException("RustBuffer.alloc() returned null data pointer (size=" + size + ")");
        }
        return buffer;
    }

    public static void free(MemorySegment buffer) {
        UniffiHelpers.uniffiRustCall((_arena, status) -> {
            UniffiLib.{{ ci.ffi_rustbuffer_free().name() }}(buffer, status);
        });
    }

    public static java.nio.ByteBuffer asByteBuffer(MemorySegment seg) {
        MemorySegment data = getData(seg);
        long len = getLen(seg);
        if (!data.equals(MemorySegment.NULL) && len > 0) {
            java.nio.ByteBuffer byteBuffer = data.reinterpret(len).asByteBuffer();
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            return byteBuffer;
        }
        return null;
    }

    public static java.nio.ByteBuffer asWriteByteBuffer(MemorySegment seg) {
        MemorySegment data = getData(seg);
        long capacity = getCapacity(seg);
        if (!data.equals(MemorySegment.NULL) && capacity > 0) {
            java.nio.ByteBuffer byteBuffer = data.reinterpret(capacity).asByteBuffer();
            byteBuffer.order(ByteOrder.BIG_ENDIAN);
            return byteBuffer;
        }
        return null;
    }
}

package {{ config.package_name() }};

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

// This is a helper for safely passing byte references into the rust code.
// It's not actually used at the moment, because there aren't many things that you
// can take a direct pointer to in the JVM, and if we're going to copy something
// then we might as well copy it into a `RustBuffer`. But it's here for API
// completeness.
public final class ForeignBytes {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_INT.withName("len"),
        MemoryLayout.paddingLayout(4),
        ValueLayout.ADDRESS.withName("data")
    );

    private static final long OFFSET_LEN = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("len"));
    private static final long OFFSET_DATA = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("data"));

    private ForeignBytes() {}

    public static int getLen(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_INT_UNALIGNED, OFFSET_LEN);
    }

    public static MemorySegment getData(MemorySegment seg) {
        return seg.get(ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA);
    }
}
