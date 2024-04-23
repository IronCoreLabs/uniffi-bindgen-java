package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

@Structure.FieldOrder("code", "error_buf")
public class UniffiRustCallStatus extends Structure {
    public byte code;
    public RustBuffer.ByValue errorBuf;

    public static class ByValue extends UniffiRustCallStatus implements Structure.ByValue {}

    public boolean isSuccess() {
        return code == UNIFFI_CALL_SUCCESS;
    }

    public boolean isError() {
        return code == UNIFFI_CALL_ERROR;
    }

    public boolean isPanic() {
        return code == UNIFFI_CALL_UNEXPECTED_ERROR;
    }

    public void setCode(byte code) {
      this.code = code;
    }

    public void setErrorBuf(RustBuffer.ByValue errorBuf) {
      this.errorBuf = errorBuf;
    }

    public static UniffiRustCallStatus.ByValue create(byte code, RustBuffer.ByValue errorBuf) {
        UniffiRustCallStatus.ByValue callStatus = new UniffiRustCallStatus.ByValue();
        callStatus.code = code;
        callStatus.errorBuf = errorBuf;
        return callStatus;
    }

    public static final byte UNIFFI_CALL_SUCCESS = 0;
    public static final byte UNIFFI_CALL_ERROR = 1;
    public static final byte UNIFFI_CALL_UNEXPECTED_ERROR = 2;
}

package {{ config.package_name() }};

public class InternalException extends Exception {
    public InternalException(String message) {
        super(message);
    }
}

package {{ config.package_name() }};

public interface UniffiRustCallStatusErrorHandler<E extends Exception> {
    E lift(RustBuffer.ByValue errorBuf);
}

package {{ config.package_name() }};

// UniffiRustCallStatusErrorHandler implementation for times when we don't expect a CALL_ERROR
class UniffiNullRustCallStatusErrorHandler implements UniffiRustCallStatusErrorHandler<InternalException> {
    @Override
    public InternalException lift(RustBuffer.ByValue errorBuf) {
        RustBuffer.free(errorBuf);
        return new InternalException("Unexpected CALL_ERROR");
    }
}

package {{ config.package_name() }};

import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;

// Helpers for calling Rust
// In practice we usually need to be synchronized to call this safely, so it doesn't
// synchronize itself
public final class UniffiHelpers {
  // Call a rust function that returns a Result<>.  Pass in the Error class companion that corresponds to the Err
  private static <U, E extends Exception> U uniffiRustCallWithError(UniffiRustCallStatusErrorHandler<E> errorHandler, Function<UniffiRustCallStatus, U> callback) {
      UniffiRustCallStatus status = new UniffiRustCallStatus();
      U returnValue = callback.call(status);
      uniffiCheckCallStatus(errorHandler, status);
      return returnValue;
  }

  // Check UniffiRustCallStatus and throw an error if the call wasn't successful
  private static <E extends Exception> void uniffiCheckCallStatus(UniffiRustCallStatusErrorHandler<E> errorHandler, UniffiRustCallStatus status) {
      if (status.isSuccess()) {
          return;
      } else if (status.isError()) {
          throw errorHandler.lift(status.errorBuf);
      } else if (status.isPanic()) {
          // when the rust code sees a panic, it tries to construct a rustbuffer
          // with the message.  but if that code panics, then it just sends back
          // an empty buffer.
          if (status.errorBuf.len > 0) {
              throw new InternalException({{ Type::String.borrow()|lift_fn  }}(status.errorBuf));
          } else {
              throw new InternalException("Rust panic");
          }
      } else {
          throw new InternalException("Unknown rust call status: " + status.code);
      }
  }

  // Call a rust function that returns a plain value
  private <U> U uniffiRustCall(Function<UniffiRustCallStatus, U> callback) {
      return uniffiRustCallWithError(new UniffiNullRustCallStatusErrorHandler(), callback);
  }

  private <T> void uniffiTraitInterfaceCall(
      UniffiRustCallStatus callStatus,
      Supplier<T> makeCall,
      Consumer<T> writeReturn
  ) {
      try {
          writeReturn.accept(makeCall.get());
      } catch (Exception e) {
          callStatus.setCode(UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
          callStatus.setErrorBuf({{ Type::String.borrow()|lower_fn }}(e.toString()));
      }
  }

  private <T, E extends Throwable> void uniffiTraitInterfaceCallWithError(
      UniffiRustCallStatus callStatus,
      Supplier<T> makeCall,
      Consumer<T> writeReturn,
      Function<E, RustBuffer.ByValue> lowerError
  ) {
      try {
          writeReturn.accept(makeCall.get());
      } catch (Exception e) {
          if (e.getClass().isAssignableFrom(E.class)) {
              @SuppressWarnings("unchecked")
              E castedE = (E) e;
              callStatus.setCode(UniffiRustCallStatus.UNIFFI_CALL_ERROR);
              callStatus.setErrorBuf(lowerError.apply(castedE));
          } else {
              callStatus.setCode(UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR);
              callStatus.setErrorBuf({{ Type::String.borrow()|lower_fn }}(e.toString()));
          }
      }
  }
}
