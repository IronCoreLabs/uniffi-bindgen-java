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

// Carries a borrowed `&[u8]` across the FFI as a (pointer, length) pair. Unlike `RustBuffer`
// the memory is owned by the caller and is only valid for the duration of the call.
// See `FfiConverterByRefBytes`.
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

// Converter for `&[u8]` / `[ByRef] bytes` arguments.
//
// Only `lower` is valid. Zero-copy byte buffers only flow foreign -> Rust, and only in argument
// position, so `lift`, `read`, `write` and `allocationSize` have no sound implementation and throw.
// `FfiConverter` is implemented anyway so the compiler enforces the full method set.
//
// The `ByteBuffer` MUST be direct: only direct buffers have a native address Rust can borrow. The
// returned segment is valid only for the duration of the call, and the caller must keep `value`
// reachable across it — Rust treats the bytes as a borrow, and a heap-collected direct buffer frees
// the memory out from under it.
public enum FfiConverterByRefBytes implements FfiConverter<java.nio.ByteBuffer, java.lang.foreign.MemorySegment> {
    INSTANCE;

    // See UniffiSlabAllocator for the design rationale.
    private static final UniffiSlabAllocator ALLOCATOR = new UniffiSlabAllocator(ForeignBytes.LAYOUT, 1024);

    @Override
    public java.lang.foreign.MemorySegment lower(java.nio.ByteBuffer value) {
        if (!value.isDirect()) {
            throw new java.lang.IllegalArgumentException(
                "UniFFI zero-copy &[u8] requires a direct ByteBuffer. Use ByteBuffer.allocateDirect().");
        }
        int remaining = value.remaining();
        java.lang.foreign.MemorySegment fb = ALLOCATOR.allocate(ForeignBytes.LAYOUT);
        ForeignBytes.setLen(fb, remaining);
        // Rust treats (null, 0) as `&[]`. Taking the address of a zero-length buffer is not
        // guaranteed to produce anything usable, so don't.
        ForeignBytes.setData(fb, remaining == 0
            ? java.lang.foreign.MemorySegment.NULL
            : java.lang.foreign.MemorySegment.ofBuffer(value));
        return fb;
    }

    @Override
    public java.nio.ByteBuffer lift(java.lang.foreign.MemorySegment value) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be lifted: zero-copy &[u8] only flows foreign->Rust");
    }

    @Override
    public java.nio.ByteBuffer read(java.nio.ByteBuffer buf) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be read from a buffer: zero-copy &[u8] is only supported in argument position, not nested in records/options/etc.");
    }

    @Override
    public void write(java.nio.ByteBuffer value, java.nio.ByteBuffer buf) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes cannot be written to a buffer: zero-copy &[u8] is only supported in argument position, not nested in records/options/etc.");
    }

    @Override
    public long allocationSize(java.nio.ByteBuffer value) {
        throw new java.lang.UnsupportedOperationException(
            "ByRef bytes have no RustBuffer allocation size: zero-copy &[u8] is only supported in argument position, not nested in records/options/etc.");
    }
}
