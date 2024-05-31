package {{ config.package_name() }};

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class UniffiAsyncHelpers {
    // Async return type handlers
    static final byte UNIFFI_RUST_FUTURE_POLL_READY = (byte) 0;
    static final byte UNIFFI_RUST_FUTURE_POLL_MAYBE_READY = (byte) 1;
    static final UniffiHandleMap<CompletableFuture<Byte>> uniffiContinuationHandleMap = UniffiHandleMap<>();

    // FFI type for Rust future continuations
    enum UniffiRustFutureContinuationCallbackImpl implements UniffiRustFutureContinuationCallback {
        INSTANCE;

        @Override
        public void callback(long data, byte pollResult) {
            uniffiContinuationHandleMap.remove(data).complete(pollResult);
        }
    }

    @FunctionalInterface
    interface PollingFunction {
        void apply(long rustFuture, UniffiRustFutureContinuationCallback callback, long continuationHandle);
    }

    static <T, F, E extends Exception> CompletableFuture<T> uniffiRustCallAsync(
        long rustFuture,
        PollingFunction pollFunc,
        BiFunction<Long, UniffiRustCallStatus, F> completeFunc,
        Consumer<Long> freeFunc,
        Function<F, T> liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        CompletableFuture<T> future = new CompletableFuture<>();

        // TODO(murph): may want an overload that takes an executor to run on.
        //   That may be misleading though, since the actual work is running in Rust's
        //   async runtime, not the provided executor.
        CompletableFuture.runAsync(() -> {
            try {
                byte pollResult;
                do {
                    pollResult = poll(rustFuture, pollFunc);
                } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

                F result = UniffiHelpers.uniffiRustCallWithError(errorHandler, status -> completeFunc.apply(rustFuture, status));
                T liftedResult = liftFunc.apply(result);
                future.complete(liftedResult);
            } catch (Exception e) {
              future.completeExceptionally(e);
            } finally {
              freeFunc.apply(rustFuture);
            }
        });

        return future;
    }

    private static byte poll(long rustFuture, PollingFunction pollFunc) throws InterruptedException, ExecutionException {
        CompletableFuture<Byte> pollFuture = new CompletableFuture<>();
        var handle = uniffiContinuationHandleMap.insert(pollFuture);
        pollFunc.apply(rustFuture, UniffiRustFutureContinuationCallbackImpl.INSTANCE, handle);

        // block until the poll completes
        return pollFuture.get();
    }
    
    {%- if ci.has_async_callback_interface_definition() %}
    static <T> UniffiForeignFuture uniffiTraitInterfaceCallAsync(
        Supplier<CompletableFuture<T>> makeCall,
        Consumer<T> handleSuccess,
        Consumer<UniffiRustCallStatus.ByValue> handleError 
    ){
        // Uniffi does its best to support structured concurrency across the FFI.
        // If the Rust future is dropped, `uniffiForeignFutureFreeImpl` is called, which will cancel the Java completable future if it's still running.
        CompletableFuture<Void> job;
        
        try {
            job = makeCall().thenAcceptAsync(handleSuccess);
        } catch(Exception e) {
            // TODO(murph): will the job be cleaned up from the foreign future map?
            handleError(
                UniffiRustCallStatus.create(
                    UNIFFI_CALL_UNEXPECTED_ERROR,
                    {{ Type::String.borrow()|lower_fn }}(e.toString()),
                )
            );
        }

        long handle = uniffiForeignFutureHandleMap.insert(job);
        return new UniffiForeignFuture(handle, uniffiForeignFutureFreeImpl);
    }

    static <T, E extends Throwable> UniffiForeignFuture uniffiTraitInterfaceCallAsyncWithError(
        Supplier<CompletableFuture<T>> makeCall,
        Consumer<T> handleSuccess,
        Consumer<UniffiRustCallStatus.ByValue> handleError, 
        Function<E, RustBuffer.ByValue> lowerError
    ){
        CompletableFuture<Void> job;
        try {
            job = makeCall().thenAcceptAsync(handleSuccess);
        } catch(Exception e) {
            if (e instanceof E) {
                handleError(
                    UniffiRustCallStatus.create(
                        UNIFFI_CALL_ERROR,
                        lowerError(e)
                    )
                );
            } else {
                handleError(
                    UniffiRustCallStatus.create(
                        UNIFFI_CALL_UNEXPECTED_ERROR,
                        {{ Type::String.borrow()|lower_fn }}(e.toString())
                    )
                );
            }
        }
        var handle = uniffiForeignFutureHandleMap.insert(job);
        return new UniffiForeignFuture(handle, uniffiForeignFutureFreeImpl);
    }

    static final UniffiHandleMap<CompletableFuture<Void>> uniffiForeignFutureHandleMap = UniffiHandleMap<>();

    enum UniffiForeignFutureFreeImpl implements UniffiForeignFutureFree {
        INSTANCE;

        public void callback(long handle) {
            var job = uniffiForeignFutureHandleMap.remove(handle);
            job.cancel(true);
        }
    }

    // For testing
    public int uniffiForeignFutureHandleCount() {
      return uniffiForeignFutureHandleMap.size();
    }

    {%- endif %}
}


