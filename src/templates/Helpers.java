package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;

public final class UniffiRustCallStatus {
    // The layout must match Rust's repr(C) RustCallStatus struct.
    // code: i8, then padding to align RustBuffer (which starts with i64),
    // then the RustBuffer error_buf.
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_BYTE.withName("code"),
        MemoryLayout.paddingLayout(7),
        RustBuffer.LAYOUT.withName("error_buf")
    );

    private static final long OFFSET_CODE = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("code"));
    private static final long ERROR_BUF_OFFSET = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("error_buf"));

    public static final byte UNIFFI_CALL_SUCCESS = 0;
    public static final byte UNIFFI_CALL_ERROR = 1;
    public static final byte UNIFFI_CALL_UNEXPECTED_ERROR = 2;

    private UniffiRustCallStatus() {}

    public static MemorySegment allocate(Arena arena) {
        MemorySegment seg = arena.allocate(LAYOUT);
        setCode(seg, UNIFFI_CALL_SUCCESS);
        return seg;
    }

    public static byte getCode(MemorySegment seg) {
        return seg.get(ValueLayout.JAVA_BYTE, OFFSET_CODE);
    }

    public static void setCode(MemorySegment seg, byte code) {
        seg.set(ValueLayout.JAVA_BYTE, OFFSET_CODE, code);
    }

    public static MemorySegment getErrorBuf(MemorySegment seg) {
        return seg.asSlice(ERROR_BUF_OFFSET, RustBuffer.LAYOUT.byteSize());
    }

    public static void setErrorBuf(MemorySegment seg, MemorySegment errorBuf) {
        MemorySegment.copy(errorBuf, 0, seg, ERROR_BUF_OFFSET, RustBuffer.LAYOUT.byteSize());
    }

    public static boolean isSuccess(MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_SUCCESS;
    }

    public static boolean isError(MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_ERROR;
    }

    public static boolean isPanic(MemorySegment seg) {
        return getCode(seg) == UNIFFI_CALL_UNEXPECTED_ERROR;
    }

    public static MemorySegment create(Arena arena, byte code, MemorySegment errorBuf) {
        MemorySegment seg = allocate(arena);
        setCode(seg, code);
        setErrorBuf(seg, errorBuf);
        return seg;
    }
}

package {{ config.package_name() }};

public class InternalException extends RuntimeException {
    public InternalException(String message) {
        super(message);
    }
}

package {{ config.package_name() }};

import java.lang.foreign.MemorySegment;

public interface UniffiRustCallStatusErrorHandler<E extends Exception> {
    E lift(MemorySegment errorBuf);
}

package {{ config.package_name() }};

import java.lang.foreign.MemorySegment;

// UniffiRustCallStatusErrorHandler implementation for times when we don't expect a CALL_ERROR
class UniffiNullRustCallStatusErrorHandler implements UniffiRustCallStatusErrorHandler<InternalException> {
    static final UniffiNullRustCallStatusErrorHandler INSTANCE = new UniffiNullRustCallStatusErrorHandler();

    @Override
    public InternalException lift(MemorySegment errorBuf) {
        RustBuffer.free(errorBuf);
        return new InternalException("Unexpected CALL_ERROR");
    }
}

package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.Callable;

// Helpers for calling Rust
// In practice we usually need to be synchronized to call this safely, so it doesn't
// synchronize itself
public final class UniffiHelpers {
  @FunctionalInterface
  interface UniffiRustCallFunction<U> {
      U apply(SegmentAllocator allocator, MemorySegment status);
  }

  @FunctionalInterface
  interface UniffiRustCallVoidFunction {
      void apply(SegmentAllocator allocator, MemorySegment status);
  }

  // Call a rust function that returns a Result<>.  Pass in the Error class companion that corresponds to the Err
  static <U, E extends Exception> U uniffiRustCallWithError(UniffiRustCallStatusErrorHandler<E> errorHandler, UniffiRustCallFunction<U> callback) throws E {
      Arena arena = Arena.ofAuto();
      MemorySegment status = UniffiRustCallStatus.allocate(arena);
      U returnValue = callback.apply(arena, status);
      uniffiCheckCallStatus(errorHandler, status);
      return returnValue;
  }

  // Overload to call a rust function that returns a Result<()>, because void is outside Java's type system.
  static <E extends Exception> void uniffiRustCallWithError(UniffiRustCallStatusErrorHandler<E> errorHandler, UniffiRustCallVoidFunction callback) throws E {
      Arena arena = Arena.ofAuto();
      MemorySegment status = UniffiRustCallStatus.allocate(arena);
      callback.apply(arena, status);
      uniffiCheckCallStatus(errorHandler, status);
  }

  // Check UniffiRustCallStatus and throw an error if the call wasn't successful
  static <E extends Exception> void uniffiCheckCallStatus(UniffiRustCallStatusErrorHandler<E> errorHandler, MemorySegment status) throws E {
      if (UniffiRustCallStatus.isSuccess(status)) {
          return;
      } else if (UniffiRustCallStatus.isError(status)) {
          throw errorHandler.lift(UniffiRustCallStatus.getErrorBuf(status));
      } else if (UniffiRustCallStatus.isPanic(status)) {
          // when the rust code sees a panic, it tries to construct a rustbuffer
          // with the message.  but if that code panics, then it just sends back
          // an empty buffer.
          MemorySegment errorBuf = UniffiRustCallStatus.getErrorBuf(status);
          if (RustBuffer.getLen(errorBuf) > 0) {
              throw new InternalException({{ Type::String.borrow()|lift_fn(config, ci)  }}(errorBuf));
          } else {
              throw new InternalException("Rust panic");
          }
      } else {
          throw new InternalException("Unknown rust call status: " + UniffiRustCallStatus.getCode(status));
      }
  }

  // Call a rust function that returns a plain value
  static <U> U uniffiRustCall(UniffiRustCallFunction<U> callback) {
      return uniffiRustCallWithError(UniffiNullRustCallStatusErrorHandler.INSTANCE, callback);
  }

  // Call a rust function that returns nothing
  static void uniffiRustCall(UniffiRustCallVoidFunction callback) {
      uniffiRustCallWithError(UniffiNullRustCallStatusErrorHandler.INSTANCE, callback);
  }

  static <T> void uniffiTraitInterfaceCall(
      MemorySegment callStatus,
      Supplier<T> makeCall,
      Consumer<T> writeReturn
  ) {
      callStatus = callStatus.reinterpret(UniffiRustCallStatus.LAYOUT.byteSize());
      try {
          writeReturn.accept(makeCall.get());
      } catch (Exception e) {
          UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
          UniffiRustCallStatus.setErrorBuf(callStatus, {{ Type::String.borrow()|lower_fn(config, ci) }}(e.toString()));
      }
  }

  static <T, E extends Throwable> void uniffiTraitInterfaceCallWithError(
      MemorySegment callStatus,
      Callable<T> makeCall,
      Consumer<T> writeReturn,
      java.util.function.Function<E, MemorySegment> lowerError,
      Class<E> errorClazz
  ) {
      callStatus = callStatus.reinterpret(UniffiRustCallStatus.LAYOUT.byteSize());
      try {
          writeReturn.accept(makeCall.call());
      } catch (Exception e) {
          if (errorClazz.isAssignableFrom(e.getClass())) {
              @SuppressWarnings("unchecked")
              E castedE = (E) e;
              UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_ERROR);
              UniffiRustCallStatus.setErrorBuf(callStatus, lowerError.apply(castedE));
          } else {
              UniffiRustCallStatus.setCode(callStatus, UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
              UniffiRustCallStatus.setErrorBuf(callStatus, {{ Type::String.borrow()|lower_fn(config, ci) }}(e.toString()));
          }
      }
  }
}
