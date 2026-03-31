package {{ config.package_name() }};

public final class UniffiRustCallStatus {
    public static final java.lang.foreign.StructLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
        java.lang.foreign.ValueLayout.JAVA_BYTE.withName("code"),
        java.lang.foreign.MemoryLayout.paddingLayout(7),  // 7 bytes padding for alignment before RustBuffer
        RustBuffer.LAYOUT.withName("error_buf")
    );

    private static final long OFFSET_CODE = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("code"));
    private static final long OFFSET_ERROR_BUF = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("error_buf"));

    public static final byte UNIFFI_CALL_SUCCESS = 0;
    public static final byte UNIFFI_CALL_ERROR = 1;
    public static final byte UNIFFI_CALL_UNEXPECTED_ERROR = 2;

    private UniffiRustCallStatus() {}

    public static byte getCode(java.lang.foreign.MemorySegment seg) {
        return seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, OFFSET_CODE);
    }

    public static void setCode(java.lang.foreign.MemorySegment seg, byte value) {
        seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, OFFSET_CODE, value);
    }

    public static java.lang.foreign.MemorySegment getErrorBuf(java.lang.foreign.MemorySegment seg) {
        return seg.asSlice(OFFSET_ERROR_BUF, RustBuffer.LAYOUT.byteSize());
    }

    public static void setErrorBuf(java.lang.foreign.MemorySegment seg, java.lang.foreign.MemorySegment errorBuf) {
        java.lang.foreign.MemorySegment.copy(errorBuf, 0, seg, OFFSET_ERROR_BUF, RustBuffer.LAYOUT.byteSize());
    }

    public static boolean isSuccess(java.lang.foreign.MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_SUCCESS;
    }

    public static boolean isError(java.lang.foreign.MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_ERROR;
    }

    public static boolean isPanic(java.lang.foreign.MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_UNEXPECTED_ERROR;
    }

    /**
     * Allocate a new RustCallStatus in the given arena.
     */
    public static java.lang.foreign.MemorySegment allocate(java.lang.foreign.SegmentAllocator allocator) {
        java.lang.foreign.MemorySegment seg = allocator.allocate(LAYOUT);
        seg.fill((byte) 0);
        return seg;
    }

    /**
     * Create a RustCallStatus with the given code and error buffer.
     * Used by async callback interface error handling.
     */
    public static java.lang.foreign.MemorySegment create(byte code, java.lang.foreign.MemorySegment errorBuf) {
        java.lang.foreign.MemorySegment seg = java.lang.foreign.Arena.global().allocate(LAYOUT);
        seg.fill((byte) 0);
        setCode(seg, code);
        setErrorBuf(seg, errorBuf);
        return seg;
    }
}

package {{ config.package_name() }};

public class InternalException extends java.lang.RuntimeException {
    public InternalException(java.lang.String message) {
        super(message);
    }
}

package {{ config.package_name() }};

public interface UniffiRustCallStatusErrorHandler<E extends java.lang.Exception> {
    E lift(java.lang.foreign.MemorySegment errorBuf);
}

package {{ config.package_name() }};

// UniffiRustCallStatusErrorHandler implementation for times when we don't expect a CALL_ERROR
class UniffiNullRustCallStatusErrorHandler implements UniffiRustCallStatusErrorHandler<InternalException> {
    @Override
    public InternalException lift(java.lang.foreign.MemorySegment errorBuf) {
        RustBuffer.free(errorBuf);
        return new InternalException("Unexpected CALL_ERROR");
    }
}

package {{ config.package_name() }};

// Helpers for calling Rust
// In practice we usually need to be synchronized to call this safely, so it doesn't
// synchronize itself
public final class UniffiHelpers {
    // Thread-local reusable RustCallStatus to avoid allocation on the hot path
    private static final ThreadLocal<java.lang.foreign.MemorySegment> REUSABLE_STATUS = ThreadLocal.withInitial(() ->
        java.lang.foreign.Arena.global().allocate(UniffiRustCallStatus.LAYOUT)
    );


    @FunctionalInterface
    public interface UniffiRustCallFunction<U> {
        U apply(java.lang.foreign.SegmentAllocator allocator, java.lang.foreign.MemorySegment status);
    }

    @FunctionalInterface
    public interface UniffiRustCallVoidFunction {
        void apply(java.lang.foreign.SegmentAllocator allocator, java.lang.foreign.MemorySegment status);
    }

    // Call a rust function that returns a Result<>. Pass in the Error class companion that
    // corresponds to the Err.
    static <U, E extends java.lang.Exception> U uniffiRustCallWithError(
            UniffiRustCallStatusErrorHandler<E> errorHandler,
            UniffiRustCallFunction<U> callback) throws E {
        java.lang.foreign.MemorySegment status = REUSABLE_STATUS.get();
        status.fill((byte) 0);
        U returnValue = callback.apply(java.lang.foreign.Arena.global(), status);
        uniffiCheckCallStatus(errorHandler, status);
        return returnValue;
    }

    // Overload for void-returning functions
    static <E extends java.lang.Exception> void uniffiRustCallWithError(
            UniffiRustCallStatusErrorHandler<E> errorHandler,
            UniffiRustCallVoidFunction callback) throws E {
        java.lang.foreign.MemorySegment status = REUSABLE_STATUS.get();
        status.fill((byte) 0);
        callback.apply(java.lang.foreign.Arena.global(), status);
        uniffiCheckCallStatus(errorHandler, status);
    }

    // Check UniffiRustCallStatus and throw an error if the call wasn't successful
    static <E extends java.lang.Exception> void uniffiCheckCallStatus(
            UniffiRustCallStatusErrorHandler<E> errorHandler,
            java.lang.foreign.MemorySegment status) throws E {
        if (UniffiRustCallStatus.isSuccess(status)) {
            return;
        } else if (UniffiRustCallStatus.isError(status)) {
            throw errorHandler.lift(UniffiRustCallStatus.getErrorBuf(status));
        } else if (UniffiRustCallStatus.isPanic(status)) {
            java.lang.foreign.MemorySegment errorBuf = UniffiRustCallStatus.getErrorBuf(status);
            if (RustBuffer.getLen(errorBuf) > 0) {
                throw new InternalException({{ Type::String.borrow()|lift_fn(config, ci) }}(errorBuf));
            } else {
                throw new InternalException("Rust panic");
            }
        } else {
            throw new InternalException("Unknown rust call status: " + UniffiRustCallStatus.getCode(status));
        }
    }

    // Primitive-specialized variants that avoid autoboxing overhead.
    // For each primitive type, we have a functional interface + call + callWithError.
    {%- for (prim, suffix) in [("long", "Long"), ("int", "Int"), ("short", "Short"), ("byte", "Byte"), ("float", "Float"), ("double", "Double"), ("boolean", "Boolean")] %}

    @FunctionalInterface
    interface UniffiRustCall{{ suffix }}Function {
        {{ prim }} apply(java.lang.foreign.SegmentAllocator allocator, java.lang.foreign.MemorySegment status);
    }

    static <E extends java.lang.Exception> {{ prim }} uniffiRustCallWithError{{ suffix }}(
            UniffiRustCallStatusErrorHandler<E> errorHandler,
            UniffiRustCall{{ suffix }}Function callback) throws E {
        java.lang.foreign.MemorySegment status = REUSABLE_STATUS.get();
        status.fill((byte) 0);
        {{ prim }} returnValue = callback.apply(java.lang.foreign.Arena.global(), status);
        uniffiCheckCallStatus(errorHandler, status);
        return returnValue;
    }

    static {{ prim }} uniffiRustCall{{ suffix }}(UniffiRustCall{{ suffix }}Function callback) {
        return uniffiRustCallWithError{{ suffix }}(new UniffiNullRustCallStatusErrorHandler(), callback);
    }
    {%- endfor %}

    // Call a rust function that returns a plain value
    static <U> U uniffiRustCall(UniffiRustCallFunction<U> callback) {
        return uniffiRustCallWithError(new UniffiNullRustCallStatusErrorHandler(), callback);
    }

    // Call a rust function that returns nothing
    static void uniffiRustCall(UniffiRustCallVoidFunction callback) {
        uniffiRustCallWithError(new UniffiNullRustCallStatusErrorHandler(), callback);
    }

    static <T> void uniffiTraitInterfaceCall(
        java.lang.foreign.MemorySegment callStatus,
        java.util.function.Supplier<T> makeCall,
        java.util.function.Consumer<T> writeReturn
    ) {
        try {
            writeReturn.accept(makeCall.get());
        } catch (java.lang.Exception e) {
            UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
            UniffiRustCallStatus.setErrorBuf(callStatus, {{ Type::String.borrow()|lower_fn(config, ci) }}(uniffiStackTraceToString(e)));
        }
    }

    private static java.lang.String uniffiStackTraceToString(java.lang.Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        } catch (java.lang.Throwable _t) {
            return e.toString();
        }
    }

    static <T, E extends java.lang.Throwable> void uniffiTraitInterfaceCallWithError(
        java.lang.foreign.MemorySegment callStatus,
        java.util.concurrent.Callable<T> makeCall,
        java.util.function.Consumer<T> writeReturn,
        java.util.function.Function<E, java.lang.foreign.MemorySegment> lowerError,
        java.lang.Class<E> errorClazz
    ) {
        try {
            writeReturn.accept(makeCall.call());
        } catch (java.lang.Exception e) {
            if (errorClazz.isAssignableFrom(e.getClass())) {
                @SuppressWarnings("unchecked")
                E castedE = (E) e;
                UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_ERROR);
                UniffiRustCallStatus.setErrorBuf(callStatus, lowerError.apply(castedE));
            } else {
                UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
                UniffiRustCallStatus.setErrorBuf(callStatus, {{ Type::String.borrow()|lower_fn(config, ci) }}(uniffiStackTraceToString(e)));
            }
        }
    }
}
