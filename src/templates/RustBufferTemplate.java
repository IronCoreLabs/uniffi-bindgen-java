package {{ config.package_name() }};

/**
 * This is a helper for safely working with byte buffers returned from the Rust code.
 * A rust-owned buffer is represented by its capacity, its current length, and a
 * pointer to the underlying data.
 */
public final class RustBuffer {
    public static final java.lang.foreign.StructLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
        java.lang.foreign.ValueLayout.JAVA_LONG.withName("capacity"),
        java.lang.foreign.ValueLayout.JAVA_LONG.withName("len"),
        java.lang.foreign.ValueLayout.ADDRESS.withName("data")
    );

    private static final long OFFSET_CAPACITY = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("capacity"));
    private static final long OFFSET_LEN = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("len"));
    private static final long OFFSET_DATA = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("data"));

    private RustBuffer() {}

    public static long getCapacity(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_CAPACITY);
    }

    public static void setCapacity(java.lang.foreign.MemorySegment seg, long value) {
        seg.set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_CAPACITY, value);
    }

    public static long getLen(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_LEN);
    }

    public static void setLen(java.lang.foreign.MemorySegment seg, long value) {
        seg.set(java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED, OFFSET_LEN, value);
    }

    public static java.lang.foreign.MemorySegment getData(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA);
    }

    public static void setData(java.lang.foreign.MemorySegment seg, java.lang.foreign.MemorySegment value) {
        seg.set(java.lang.foreign.ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA, value);
    }

    public static java.lang.foreign.MemorySegment alloc(long size) {
        java.lang.foreign.MemorySegment buffer = UniffiHelpers.uniffiRustCall((_allocator, status) -> {
            return UniffiLib.{{ ci.ffi_rustbuffer_alloc().name() }}(_allocator, size, status);
        });
        if (getData(buffer).equals(java.lang.foreign.MemorySegment.NULL) && size > 0) {
            throw new java.lang.RuntimeException("RustBuffer.alloc() returned null data pointer (size=" + size + ")");
        }
        return buffer;
    }

    public static void free(java.lang.foreign.MemorySegment buffer) {
        UniffiHelpers.uniffiRustCall((_allocator, status) -> {
            UniffiLib.{{ ci.ffi_rustbuffer_free().name() }}(buffer, status);
            return null;
        });
    }

    /**
     * Get a ByteBuffer view of the data for reading (len bytes).
     */
    public static java.nio.ByteBuffer asByteBuffer(java.lang.foreign.MemorySegment seg) {
        long len = getLen(seg);
        if (len == 0) {
            return java.nio.ByteBuffer.allocate(0).order(java.nio.ByteOrder.BIG_ENDIAN);
        }
        return getData(seg).reinterpret(len).asByteBuffer().order(java.nio.ByteOrder.BIG_ENDIAN);
    }

    /**
     * Get a ByteBuffer view of the data for writing (capacity bytes).
     */
    public static java.nio.ByteBuffer asWriteByteBuffer(java.lang.foreign.MemorySegment seg) {
        long capacity = getCapacity(seg);
        if (capacity == 0) {
            return java.nio.ByteBuffer.allocate(0).order(java.nio.ByteOrder.BIG_ENDIAN);
        }
        return getData(seg).reinterpret(capacity).asByteBuffer().order(java.nio.ByteOrder.BIG_ENDIAN);
    }
}

package {{ config.package_name() }};

// This is a helper for safely passing byte references into the rust code.
// It's not actually used at the moment, because there aren't many things that you
// can take a direct pointer to in the JVM, and if we're going to copy something
// then we might as well copy it into a `RustBuffer`. But it's here for API
// completeness.
public final class ForeignBytes {
    public static final java.lang.foreign.StructLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
        java.lang.foreign.ValueLayout.JAVA_INT.withName("len"),
        java.lang.foreign.MemoryLayout.paddingLayout(4),  // 4 bytes padding for alignment before ADDRESS
        java.lang.foreign.ValueLayout.ADDRESS.withName("data")
    );

    private static final long OFFSET_LEN = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("len"));
    private static final long OFFSET_DATA = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("data"));

    private ForeignBytes() {}

    public static int getLen(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, OFFSET_LEN);
    }

    public static void setLen(java.lang.foreign.MemorySegment seg, int value) {
        seg.set(java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED, OFFSET_LEN, value);
    }

    public static java.lang.foreign.MemorySegment getData(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA);
    }

    public static void setData(java.lang.foreign.MemorySegment seg, java.lang.foreign.MemorySegment value) {
        seg.set(java.lang.foreign.ValueLayout.ADDRESS_UNALIGNED, OFFSET_DATA, value);
    }
}
