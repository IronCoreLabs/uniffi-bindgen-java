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

// Pointer + length for bytes owned by the JVM and borrowed by Rust for the duration of one call.
// Used for `&[u8]` / `[ByRef] bytes` arguments; see FfiConverterByRefBytes.
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

package {{ config.package_name() }};

// Lowers `&[u8]` / `[ByRef] bytes` arguments, which Rust borrows for the duration of the call
// rather than taking ownership of a RustBuffer.
//
// Only `lower` is reachable: zero-copy bytes flow foreign -> Rust in argument position only, so a
// borrow can never be lifted or serialized. `FfiConverter` is implemented anyway so the compiler
// enforces the full set.
//
// The buffer must not be mutated by another thread while a call is in flight, since Rust is reading
// it directly.
public enum FfiConverterByRefBytes implements FfiConverter<java.nio.ByteBuffer, java.lang.foreign.MemorySegment> {
    INSTANCE;

    // The struct is read by Rust during the call, so each one needs its own slice; see
    // UniffiSlabAllocator.
    private static final UniffiSlabAllocator ALLOCATOR = new UniffiSlabAllocator(ForeignBytes.LAYOUT, 1024);

    @Override
    public java.lang.foreign.MemorySegment lower(java.nio.ByteBuffer value) {
        if (!value.isDirect()) {
            throw new java.lang.IllegalArgumentException(
                "UniFFI zero-copy &[u8] requires a direct ByteBuffer, so Rust can borrow it without "
                + "a copy. Convert with: ByteBuffer.allocateDirect(arr.length).put(arr).flip()");
        }
        java.lang.foreign.MemorySegment fb = ALLOCATOR.allocate(ForeignBytes.LAYOUT);
        int remaining = value.remaining();
        ForeignBytes.setLen(fb, remaining);
        // A zero-length direct buffer has no meaningful address; Rust reads (null, 0) as `&[]`.
        // Otherwise ofBuffer honours position/limit, so Rust sees exactly the remaining slice.
        ForeignBytes.setData(fb, remaining == 0
            ? java.lang.foreign.MemorySegment.NULL
            : java.lang.foreign.MemorySegment.ofBuffer(value));
        return fb;
    }

    @Override
    public java.nio.ByteBuffer lift(java.lang.foreign.MemorySegment value) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be lifted: zero-copy &[u8] only flows foreign to Rust");
    }

    @Override
    public java.nio.ByteBuffer read(java.nio.ByteBuffer buf) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be read from a buffer: zero-copy &[u8] is only supported in argument position");
    }

    @Override
    public void write(java.nio.ByteBuffer value, java.nio.ByteBuffer buf) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be written to a buffer: zero-copy &[u8] is only supported in argument position");
    }

    @Override
    public long allocationSize(java.nio.ByteBuffer value) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes have no RustBuffer allocation size: zero-copy &[u8] is only supported in argument position");
    }
}
