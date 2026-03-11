package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class UniffiAsyncHelpers {
    // Async return type handlers
    static final byte UNIFFI_RUST_FUTURE_POLL_READY = (byte) 0;
    static final byte UNIFFI_RUST_FUTURE_POLL_MAYBE_READY = (byte) 1;
    static final UniffiHandleMap<CompletableFuture<Byte>> uniffiContinuationHandleMap = new UniffiHandleMap<>();
    static final UniffiHandleMap<CancelableForeignFuture> uniffiForeignFutureHandleMap = new UniffiHandleMap<>();

    // Upcall stub for the continuation callback - created once and reused
    private static final Arena CALLBACK_ARENA = Arena.ofAuto();
    private static final MemorySegment CONTINUATION_CALLBACK_STUB;
    static {
        CONTINUATION_CALLBACK_STUB = {{ "RustFutureContinuationCallback"|ffi_callback_name }}.toUpcallStub(
            (long data, byte pollResult) -> {
                uniffiContinuationHandleMap.remove(data).complete(pollResult);
            },
            CALLBACK_ARENA
        );
    }

    @FunctionalInterface
    interface PollingFunction {
        void apply(long rustFuture, MemorySegment callback, long continuationHandle);
    }

    static class UniffiFreeingFuture<T> extends CompletableFuture<T> {
        private Consumer<Long> freeFunc;
        private long rustFuture;

        public UniffiFreeingFuture(long rustFuture, Consumer<Long> freeFunc) {
            this.freeFunc = freeFunc;
            this.rustFuture = rustFuture;
        }

        @Override
        public boolean cancel(boolean ignored) {
            boolean cancelled = super.cancel(ignored);
            if (cancelled) {
                freeFunc.accept(rustFuture);
            }
            return cancelled;
        }
    }

    // helper so both the Java completable future and the job that handles it finishing and reports to Rust can be
    // retrieved (and potentially cancelled) by handle. This allows our FreeImpl to be a parameterless singleton,
    // preventing #19, which was caused by our FreeImpls being GCd before Rust called back into them.
    static class CancelableForeignFuture {
        private CompletableFuture<?> childFuture;
        private CompletableFuture<Void> childFutureHandler;

        CancelableForeignFuture(CompletableFuture<?> childFuture, CompletableFuture<Void> childFutureHandler) {
            this.childFuture = childFuture;
            this.childFutureHandler = childFutureHandler;
        }

        public void cancel() {
            var successfullyCancelled = this.childFutureHandler.cancel(true);
            if(successfullyCancelled) {
                childFuture.cancel(true);
            }
        }
    }

    // Simple data holder for returning a foreign future handle + free stub from
    // uniffiTraitInterfaceCallAsync. Not the same as the FFI struct class UniffiForeignFuture.
    static class UniffiForeignFutureData {
        final long handle;
        final MemorySegment free;

        UniffiForeignFutureData(long handle, MemorySegment free) {
            this.handle = handle;
            this.free = free;
        }
    }

    @FunctionalInterface
    interface AsyncCompleteFunction<F> {
        F apply(Arena arena, long rustFuture, MemorySegment status);
    }

    @FunctionalInterface
    interface AsyncCompleteVoidFunction {
        void apply(Arena arena, long rustFuture, MemorySegment status);
    }

    static <T, F, E extends Exception> CompletableFuture<T> uniffiRustCallAsync(
        long rustFuture,
        PollingFunction pollFunc,
        AsyncCompleteFunction<F> completeFunc,
        Consumer<Long> freeFunc,
        Function<F, T> liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        CompletableFuture<T> future = new UniffiFreeingFuture<>(rustFuture, freeFunc);

        CompletableFuture.runAsync(() -> {
            try {
                byte pollResult;
                do {
                    pollResult = poll(rustFuture, pollFunc);
                } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

                if (!future.isCancelled()) {
                    F result = UniffiHelpers.uniffiRustCallWithError(errorHandler, (_arena, status) -> {
                        return completeFunc.apply(_arena, rustFuture, status);
                    });
                    T liftedResult = liftFunc.apply(result);
                    future.complete(liftedResult);
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                if (!future.isCancelled()) {
                    freeFunc.accept(rustFuture);
                }
            }
        });

        return future;
    }


    // overload specifically for Void cases, which aren't within the Object type.
    // This is only necessary because of Java's lack of proper Any/Unit
    static <E extends Exception> CompletableFuture<Void> uniffiRustCallAsync(
        long rustFuture,
        PollingFunction pollFunc,
        AsyncCompleteVoidFunction completeFunc,
        Consumer<Long> freeFunc,
        Runnable liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        CompletableFuture<Void> future = new UniffiFreeingFuture<>(rustFuture, freeFunc);

        CompletableFuture.runAsync(() -> {
            try {
                byte pollResult;
                do {
                    pollResult = poll(rustFuture, pollFunc);
                } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

                if (!future.isCancelled()) {
                    UniffiHelpers.uniffiRustCallWithError(errorHandler, (_arena, status) -> {
                        completeFunc.apply(_arena, rustFuture, status);
                    });
                    future.complete(null);
                }
            } catch (Throwable e) {
                future.completeExceptionally(e);
            } finally {
                if (!future.isCancelled()) {
                    freeFunc.accept(rustFuture);
                }
            }
        });

        return future;
    }

    private static byte poll(long rustFuture, PollingFunction pollFunc) throws InterruptedException, ExecutionException {
        CompletableFuture<Byte> pollFuture = new CompletableFuture<>();
        Thread currentThread = Thread.currentThread();
        pollFuture.whenComplete((result, error) -> LockSupport.unpark(currentThread));
        var handle = uniffiContinuationHandleMap.insert(pollFuture);
        pollFunc.apply(rustFuture, CONTINUATION_CALLBACK_STUB, handle);
        while (!pollFuture.isDone()) {
            LockSupport.park();
        }
        return pollFuture.get();
    }

    {%- if ci.has_async_callback_interface_definition() %}
    static <T> UniffiForeignFutureData uniffiTraitInterfaceCallAsync(
        Supplier<CompletableFuture<T>> makeCall,
        Consumer<T> handleSuccess,
        Consumer<MemorySegment> handleError
    ){
        // Uniffi does its best to support structured concurrency across the FFI.
        // If the Rust future is dropped, `UniffiForeignFutureFreeImpl` is called, which will cancel the Java completable future if it's still running.
        var foreignFutureCf = makeCall.get();
        CompletableFuture<Void> ffHandler = foreignFutureCf.<Void>handleAsync((result, error) -> {
            if (error != null) {
                // handleAsync wraps exceptions in CompletionException; unwrap to get the real cause
                Throwable cause = (error instanceof java.util.concurrent.CompletionException && error.getCause() != null)
                    ? error.getCause() : error;
                Arena arena = Arena.ofAuto();
                handleError.accept(
                    UniffiRustCallStatus.create(
                        arena,
                        UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                        {{ Type::String.borrow()|lower_fn(config, ci) }}(cause.toString())
                    )
                );
            } else {
                try {
                    handleSuccess.accept(result);
                } catch (Throwable e) {
                    Arena arena = Arena.ofAuto();
                    handleError.accept(
                        UniffiRustCallStatus.create(
                            arena,
                            UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                            {{ Type::String.borrow()|lower_fn(config, ci) }}(e.toString())
                        )
                    );
                }
            }
            return null;
        });
        long handle = uniffiForeignFutureHandleMap.insert(new CancelableForeignFuture(foreignFutureCf, ffHandler));
        return new UniffiForeignFutureData(handle, FOREIGN_FUTURE_FREE_STUB);
    }

    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> UniffiForeignFutureData uniffiTraitInterfaceCallAsyncWithError(
        Supplier<CompletableFuture<T>> makeCall,
        Consumer<T> handleSuccess,
        Consumer<MemorySegment> handleError,
        Function<E, MemorySegment> lowerError,
        Class<E> errorClass
    ){
        var foreignFutureCf = makeCall.get();
        CompletableFuture<Void> ffHandler = foreignFutureCf.<Void>handleAsync((result, error) -> {
            if (error != null) {
                // handleAsync wraps exceptions in CompletionException; unwrap to get the real cause
                Throwable cause = (error instanceof java.util.concurrent.CompletionException && error.getCause() != null)
                    ? error.getCause() : error;
                Arena arena = Arena.ofAuto();
                if (errorClass.isInstance(cause)) {
                    handleError.accept(
                        UniffiRustCallStatus.create(
                            arena,
                            UniffiRustCallStatus.UNIFFI_CALL_ERROR,
                            lowerError.apply((E) cause)
                        )
                    );
                } else {
                    handleError.accept(
                        UniffiRustCallStatus.create(
                            arena,
                            UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                            {{ Type::String.borrow()|lower_fn(config, ci) }}(cause.getMessage())
                        )
                    );
                }
            } else {
                try {
                    handleSuccess.accept(result);
                } catch (Throwable e) {
                    Arena arena = Arena.ofAuto();
                    if (errorClass.isInstance(e)) {
                        handleError.accept(
                            UniffiRustCallStatus.create(
                                arena,
                                UniffiRustCallStatus.UNIFFI_CALL_ERROR,
                                lowerError.apply((E) e)
                            )
                        );
                    } else {
                        handleError.accept(
                            UniffiRustCallStatus.create(
                                arena,
                                UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                                {{ Type::String.borrow()|lower_fn(config, ci) }}(e.getMessage())
                            )
                        );
                    }
                }
            }
            return null;
        });
        long handle = uniffiForeignFutureHandleMap.insert(new CancelableForeignFuture(foreignFutureCf, ffHandler));
        return new UniffiForeignFutureData(handle, FOREIGN_FUTURE_FREE_STUB);
    }

    private static final MemorySegment FOREIGN_FUTURE_FREE_STUB =
        {{ "ForeignFutureFree"|ffi_callback_name }}.toUpcallStub(
            (long handle) -> {
                var futureWithHandler = uniffiForeignFutureHandleMap.remove(handle);
                futureWithHandler.cancel();
            },
            CALLBACK_ARENA
        );

    // For testing
    public static int uniffiForeignFutureHandleCount() {
      return uniffiForeignFutureHandleMap.size();
    }
    {%- endif %}
}


