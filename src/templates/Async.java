package {{ config.package_name() }};

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class UniffiAsyncHelpers {
    // Async return type handlers
    static final byte UNIFFI_RUST_FUTURE_POLL_READY = (byte) 0;
    static final byte UNIFFI_RUST_FUTURE_POLL_MAYBE_READY = (byte) 1;
    static final UniffiHandleMap<CompletableFuture<Byte>> uniffiContinuationHandleMap = new UniffiHandleMap<>();
    static final UniffiHandleMap<CompletableFuture<Void>> uniffiForeignFutureHandleMap = new UniffiHandleMap<>();

    // FFI type for Rust future continuations
    enum UniffiRustFutureContinuationCallbackImpl implements UniffiRustFutureContinuationCallback {
        INSTANCE;

        @Override
        public void callback(long data, byte pollResult) {
            System.out.println("Rust Future Java callback called for continuationHandle " + data + ": " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
            uniffiContinuationHandleMap.remove(data).complete(pollResult);
            System.out.println("Rust Future Java callback for continuationHandle " + data + " completed: " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
        }
    }

    @FunctionalInterface
    interface PollingFunction {
        void apply(long rustFuture, UniffiRustFutureContinuationCallback callback, long continuationHandle);
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

    static <T, F, E extends Exception> CompletableFuture<T> uniffiRustCallAsync(
        long rustFuture,
        PollingFunction pollFunc,
        BiFunction<Long, UniffiRustCallStatus, F> completeFunc,
        Consumer<Long> freeFunc,
        Function<F, T> liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        CompletableFuture<T> future = new UniffiFreeingFuture<>(rustFuture, freeFunc);

        CompletableFuture.runAsync(() -> {
            try {
                byte pollResult;
                do {
                    System.out.println("Polling Rust Future " + rustFuture + " from Java: " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
                    pollResult = poll(rustFuture, pollFunc);
                    System.out.println("Finished polling Rust Future " + rustFuture + " from Java: " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
                } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

                if (!future.isCancelled()) {
                    F result = UniffiHelpers.uniffiRustCallWithError(errorHandler, status -> {
                        return completeFunc.apply(rustFuture, status);
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
        BiConsumer<Long, UniffiRustCallStatus> completeFunc,
        Consumer<Long> freeFunc,
        Runnable liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        CompletableFuture<Void> future = new UniffiFreeingFuture<>(rustFuture, freeFunc);
        
        CompletableFuture.runAsync(() -> {
            try {
                byte pollResult;
                do {
                    System.out.println("Polling Rust Future " + rustFuture + " from Java: " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
                    pollResult = poll(rustFuture, pollFunc);
                    System.out.println("Finished polling Rust Future " + rustFuture + " from Java: " + java.time.Instant.now().toEpochMilli() + "\n" + "    thread name: " + Thread.currentThread().getName());
                } while (pollResult != UNIFFI_RUST_FUTURE_POLL_READY);

                // even though the outer `future` has been cancelled, this inner `runAsync` is unsupervised
                // and keeps running. When it calls `completeFunc` after being cancelled, it's status is `SUCCESS`
                // (assuming the Rust part succeeded), and the function being called can lead to a core dump.
                // Guarding with `isCancelled` here makes everything work, but feels like a cludge.
                if (!future.isCancelled()) {
                    UniffiHelpers.uniffiRustCallWithError(errorHandler, status -> {
                        completeFunc.accept(rustFuture, status);
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
        var handle = uniffiContinuationHandleMap.insert(pollFuture);
        System.out.println("Calling extern poll function for RustFuture handle " + rustFuture + ", continuationHandle " + handle + " from Java: " + java.time.Instant.now().toEpochMilli());
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
        // If the Rust future is dropped, `UniffiForeignFutureFreeImpl` is called, which will cancel the Java completable future if it's still running.
        var foreignFutureCf = makeCall.get();
        CompletableFuture<Void> job = CompletableFuture.supplyAsync(() -> {
            try {
                foreignFutureCf.thenAcceptAsync(handleSuccess).get();
            } catch(Throwable e) {
                // if we errored inside the CF, it's that error we want to send to Rust, not the wrapper
                if (e instanceof ExecutionException) {
                    e = e.getCause();
                }
                handleError.accept(
                    UniffiRustCallStatus.create(
                        UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                        {{ Type::String.borrow()|lower_fn(config) }}(e.toString())
                    )
                );
            }

            return null;
        });
        
        long handle = uniffiForeignFutureHandleMap.insert(job);
        return new UniffiForeignFuture(handle, new UniffiForeignFutureFreeImpl(foreignFutureCf));
    }

    @SuppressWarnings("unchecked")
    static <T, E extends Throwable> UniffiForeignFuture uniffiTraitInterfaceCallAsyncWithError(
        Supplier<CompletableFuture<T>> makeCall,
        Consumer<T> handleSuccess,
        Consumer<UniffiRustCallStatus.ByValue> handleError, 
        Function<E, RustBuffer.ByValue> lowerError,
        Class<E> errorClass
    ){
        var foreignFutureCf = makeCall.get();
        CompletableFuture<Void> job = CompletableFuture.supplyAsync(() -> {
            try {
                foreignFutureCf.thenAcceptAsync(handleSuccess).get();
            } catch (Throwable e) {
                // if we errored inside the CF, it's that error we want to send to Rust, not the wrapper
                if (e instanceof ExecutionException) {
                    e = e.getCause();
                }
                if (errorClass.isInstance(e)) {
                    handleError.accept(
                        UniffiRustCallStatus.create(
                            UniffiRustCallStatus.UNIFFI_CALL_ERROR,
                            lowerError.apply((E) e)
                        )
                    );
                } else {
                    handleError.accept(
                        UniffiRustCallStatus.create(
                            UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                            {{ Type::String.borrow()|lower_fn(config) }}(e.getMessage())
                        )
                    );
                }
            }

            return null;
        });

        long handle = uniffiForeignFutureHandleMap.insert(job);
        return new UniffiForeignFuture(handle, new UniffiForeignFutureFreeImpl(foreignFutureCf));
    }

    static class UniffiForeignFutureFreeImpl<T> implements UniffiForeignFutureFree {
        private CompletableFuture<T> childFuture;

        UniffiForeignFutureFreeImpl(CompletableFuture<T> childFuture) {
            this.childFuture = childFuture;
        }

        @Override
        public void callback(long handle) {
            System.out.println("ForeignFutureFreeImpl called from test: " + java.time.Instant.now().toEpochMilli());
            var job = uniffiForeignFutureHandleMap.remove(handle);
            var successfullyCancelled = job.cancel(true);
            if(successfullyCancelled) {
                childFuture.cancel(true);
            }
        }
    }

    // For testing
    public static int uniffiForeignFutureHandleCount() {
      return uniffiForeignFutureHandleMap.size();
    }
    {%- endif %}
}


