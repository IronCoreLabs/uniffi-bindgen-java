package {{ config.package_name() }};

public final class UniffiAsyncHelpers {
    // Async return type handlers
    static final byte UNIFFI_RUST_FUTURE_POLL_READY = (byte) 0;
    static final byte UNIFFI_RUST_FUTURE_POLL_WAKE = (byte) 1;
    static final UniffiHandleMap<java.util.concurrent.CompletableFuture<java.lang.Byte>> uniffiContinuationHandleMap = new UniffiHandleMap<>();
    static final UniffiHandleMap<CancelableForeignFuture> uniffiForeignFutureHandleMap = new UniffiHandleMap<>();

    // Singleton upcall stub for the continuation callback, created once and reused
    private static final java.lang.foreign.MemorySegment CONTINUATION_CALLBACK_STUB;
    static {
        try {
            java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findStatic(UniffiAsyncHelpers.class, "continuationCallback",
                    java.lang.invoke.MethodType.methodType(void.class, long.class, byte.class));
            CONTINUATION_CALLBACK_STUB = java.lang.foreign.Linker.nativeLinker().upcallStub(
                handle,
                java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_LONG, java.lang.foreign.ValueLayout.JAVA_BYTE),
                java.lang.foreign.Arena.ofAuto()
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Failed to create continuation callback stub", e);
        }
    }

    // The actual callback implementation invoked from native code
    static void continuationCallback(long data, byte pollResult) {
        uniffiContinuationHandleMap.remove(data).complete(pollResult);
    }

    @FunctionalInterface
    interface PollingFunction {
        void apply(long rustFuture, java.lang.foreign.MemorySegment callback, long continuationHandle);
    }

    @FunctionalInterface
    interface AsyncCompleteFunction<F> {
        F apply(java.lang.foreign.SegmentAllocator allocator, long rustFuture, java.lang.foreign.MemorySegment status);
    }

    @FunctionalInterface
    interface AsyncCompleteVoidFunction {
        void apply(java.lang.foreign.SegmentAllocator allocator, long rustFuture, java.lang.foreign.MemorySegment status);
    }

    // The pipeline is the sole freer; cancel() only signals Rust, which fires the in-flight poll's
    // continuation and makes later polls return Ready. A failed tryLock means free() is in progress
    // and there is nothing left to cancel. Under an inline executor rust_future_cancel invokes that
    // continuation synchronously, so the critical section extends through the completion pipeline
    // and a reentrant free().
    static final class UniffiFreeingFuture<T> extends java.util.concurrent.CompletableFuture<T> {
        private final long rustFuture;
        private final java.util.function.Consumer<java.lang.Long> cancelFunc;
        private final java.util.function.Consumer<java.lang.Long> freeFunc;
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        private boolean freed;

        UniffiFreeingFuture(
            long rustFuture,
            java.util.function.Consumer<java.lang.Long> cancelFunc,
            java.util.function.Consumer<java.lang.Long> freeFunc
        ) {
            this.rustFuture = rustFuture;
            this.cancelFunc = cancelFunc;
            this.freeFunc = freeFunc;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled && lock.tryLock()) {
                try {
                    if (!freed) {
                        cancelFunc.accept(rustFuture);
                    }
                } finally {
                    lock.unlock();
                }
            }
            return cancelled;
        }

        void free() {
            lock.lock();
            try {
                if (!freed) {
                    freed = true;
                    freeFunc.accept(rustFuture);
                }
            } finally {
                lock.unlock();
            }
        }
    }

    // Helper so both the Java completable future and the job that handles it finishing and
    // reports to Rust can be retrieved (and potentially cancelled) by handle.
    static class CancelableForeignFuture {
        private java.util.concurrent.CompletableFuture<?> childFuture;
        private java.util.concurrent.CompletableFuture<java.lang.Void> childFutureHandler;

        CancelableForeignFuture(java.util.concurrent.CompletableFuture<?> childFuture, java.util.concurrent.CompletableFuture<java.lang.Void> childFutureHandler) {
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

    static <T, F, E extends java.lang.Exception> java.util.concurrent.CompletableFuture<T> uniffiRustCallAsync(
        java.util.concurrent.Executor uniffiExecutor,
        long rustFuture,
        PollingFunction pollFunc,
        AsyncCompleteFunction<F> completeFunc,
        java.util.function.Consumer<java.lang.Long> cancelFunc,
        java.util.function.Consumer<java.lang.Long> freeFunc,
        java.util.function.Function<F, T> liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        UniffiFreeingFuture<T> future = new UniffiFreeingFuture<>(rustFuture, cancelFunc, freeFunc);

        java.util.concurrent.CompletableFuture<java.lang.Void> pollChain;
        try {
            pollChain = pollUntilReady(rustFuture, pollFunc, uniffiExecutor);
        } catch (java.lang.Exception e) {
            future.completeExceptionally(e);
            future.free();
            return future;
        }

        pollChain.thenApplyAsync(ignored -> {
            if (future.isCancelled()) {
                return null;
            }
            try {
                F result = UniffiHelpers.uniffiRustCallWithError(errorHandler, (_allocator, status) -> {
                    return completeFunc.apply(_allocator, rustFuture, status);
                });
                return liftFunc.apply(result);
            } catch (java.lang.Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, uniffiExecutor).whenComplete((result, throwable) -> {
            try {
                if (throwable != null) {
                    java.lang.Throwable cause = throwable;
                    if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    future.completeExceptionally(cause);
                } else {
                    future.complete(result);
                }
            } finally {
                future.free();
            }
        });

        return future;
    }


    // Overload specifically for Void cases, which aren't within the Object type.
    // This is only necessary because of Java's lack of proper Any/Unit.
    static <E extends java.lang.Exception> java.util.concurrent.CompletableFuture<java.lang.Void> uniffiRustCallAsync(
        java.util.concurrent.Executor uniffiExecutor,
        long rustFuture,
        PollingFunction pollFunc,
        AsyncCompleteVoidFunction completeFunc,
        java.util.function.Consumer<java.lang.Long> cancelFunc,
        java.util.function.Consumer<java.lang.Long> freeFunc,
        java.lang.Runnable liftFunc,
        UniffiRustCallStatusErrorHandler<E> errorHandler
    ){
        UniffiFreeingFuture<java.lang.Void> future = new UniffiFreeingFuture<>(rustFuture, cancelFunc, freeFunc);

        java.util.concurrent.CompletableFuture<java.lang.Void> pollChain;
        try {
            pollChain = pollUntilReady(rustFuture, pollFunc, uniffiExecutor);
        } catch (java.lang.Exception e) {
            future.completeExceptionally(e);
            future.free();
            return future;
        }

        pollChain.<java.lang.Void>thenApplyAsync(ignored -> {
            if (future.isCancelled()) {
                return null;
            }
            try {
                UniffiHelpers.uniffiRustCallWithError(errorHandler, (_allocator, status) -> {
                    completeFunc.apply(_allocator, rustFuture, status);
                });
            } catch (java.lang.Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
            return null;
        }, uniffiExecutor).whenComplete((result, throwable) -> {
            try {
                if (throwable != null) {
                    java.lang.Throwable cause = throwable;
                    if (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    future.completeExceptionally(cause);
                } else {
                    future.complete(null);
                }
            } finally {
                future.free();
            }
        });

        return future;
    }

    private static java.util.concurrent.CompletableFuture<java.lang.Void> pollUntilReady(long rustFuture, PollingFunction pollFunc, java.util.concurrent.Executor uniffiExecutor) {
        java.util.concurrent.CompletableFuture<java.lang.Byte> pollFuture = new java.util.concurrent.CompletableFuture<>();
        var handle = uniffiContinuationHandleMap.insert(pollFuture);
        try {
            pollFunc.apply(rustFuture, CONTINUATION_CALLBACK_STUB, handle);
        } catch (java.lang.Throwable e) {
            // Rust never took the handle, so nothing else will remove it.
            uniffiContinuationHandleMap.remove(handle);
            throw e;
        }
        return pollFuture.thenComposeAsync(pollResult -> {
            if (pollResult == UNIFFI_RUST_FUTURE_POLL_READY) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            } else {
                return pollUntilReady(rustFuture, pollFunc, uniffiExecutor);
            }
        }, uniffiExecutor);
    }

    {%- if ci.has_async_callback_interface_definition() %}
    static <T> void uniffiTraitInterfaceCallAsync(
        java.util.function.Supplier<java.util.concurrent.CompletableFuture<T>> makeCall,
        java.util.function.Consumer<T> handleSuccess,
        java.util.function.Consumer<java.lang.foreign.MemorySegment> handleError,
        java.lang.foreign.MemorySegment uniffiOutDroppedCallback
    ){
        // Uniffi does its best to support structured concurrency across the FFI.
        // If the Rust future is dropped, the free callback is invoked, which will
        // cancel the Java completable future if it's still running.

        // Upcall parameter segments have zero size; reinterpret to actual struct size
        uniffiOutDroppedCallback = uniffiOutDroppedCallback.reinterpret({{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.LAYOUT.byteSize());
        var foreignFutureCf = makeCall.get();
        java.util.concurrent.CompletableFuture<java.lang.Void> ffHandler = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            // Note: it's important we call either `handleSuccess` or `handleError` exactly once.
            // Each call consumes an Arc reference, which means there should be no possibility of
            // a double call. The following code is structured so that we will never call both
            // `handleSuccess` and `handleError`, even in the face of weird exceptions.
            //
            // In extreme circumstances we may not call either, for example if we fail to invoke
            // `handleSuccess`. This means we will leak the Arc reference, which is
            // better than double-freeing it.
            T callResult;
            try {
                callResult = foreignFutureCf.get();
            } catch(java.lang.Throwable e) {
                // If we errored inside the CF, it's that error we want to send to Rust, not the wrapper
                if (e instanceof java.util.concurrent.ExecutionException) {
                    e = e.getCause();
                }
                handleError.accept(
                    UniffiRustCallStatus.create(
                        UniffiRustCallStatus.UNIFFI_CALL_UNEXPECTED_ERROR,
                        {{ Type::String.borrow()|lower_fn(config, ci) }}(uniffiStackTraceToString(e))
                    )
                );
                return null;
            }
            handleSuccess.accept(callResult);
            return null;
        });
        long handle = uniffiForeignFutureHandleMap.insert(new CancelableForeignFuture(foreignFutureCf, ffHandler));
        // Write handle and free callback into the ForeignFuture struct
        {{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.sethandle(uniffiOutDroppedCallback, handle);
        {{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.setfree(uniffiOutDroppedCallback, FOREIGN_FUTURE_DROPPED_CALLBACK_STUB);
    }

    @SuppressWarnings("unchecked")
    static <T, E extends java.lang.Throwable> void uniffiTraitInterfaceCallAsyncWithError(
        java.util.function.Supplier<java.util.concurrent.CompletableFuture<T>> makeCall,
        java.util.function.Consumer<T> handleSuccess,
        java.util.function.Consumer<java.lang.foreign.MemorySegment> handleError,
        java.util.function.Function<E, java.lang.foreign.MemorySegment> lowerError,
        java.lang.Class<E> errorClass,
        java.lang.foreign.MemorySegment uniffiOutDroppedCallback
    ){
        // Upcall parameter segments have zero size; reinterpret to actual struct size
        uniffiOutDroppedCallback = uniffiOutDroppedCallback.reinterpret({{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.LAYOUT.byteSize());
        var foreignFutureCf = makeCall.get();
        java.util.concurrent.CompletableFuture<java.lang.Void> ffHandler = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            // See the note in uniffiTraitInterfaceCallAsync for details on `handleSuccess` and
            // `handleError`.
            T callResult;
            try {
                callResult = foreignFutureCf.get();
            } catch (java.lang.Throwable e) {
                // If we errored inside the CF, it's that error we want to send to Rust, not the wrapper
                if (e instanceof java.util.concurrent.ExecutionException) {
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
                            {{ Type::String.borrow()|lower_fn(config, ci) }}(uniffiStackTraceToString(e))
                        )
                    );
                }
                return null;
            }
            handleSuccess.accept(callResult);
            return null;
        });

        long handle = uniffiForeignFutureHandleMap.insert(new CancelableForeignFuture(foreignFutureCf, ffHandler));
        {{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.sethandle(uniffiOutDroppedCallback, handle);
        {{ "ForeignFutureDroppedCallbackStruct"|ffi_struct_name }}.setfree(uniffiOutDroppedCallback, FOREIGN_FUTURE_DROPPED_CALLBACK_STUB);
    }

    // Singleton upcall stub for foreign future dropped callback
    private static final java.lang.foreign.MemorySegment FOREIGN_FUTURE_DROPPED_CALLBACK_STUB;
    static {
        try {
            java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findStatic(UniffiAsyncHelpers.class, "foreignFutureDroppedCallback",
                    java.lang.invoke.MethodType.methodType(void.class, long.class));
            FOREIGN_FUTURE_DROPPED_CALLBACK_STUB = java.lang.foreign.Linker.nativeLinker().upcallStub(
                handle,
                java.lang.foreign.FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_LONG),
                java.lang.foreign.Arena.ofAuto()
            );
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Failed to create foreign future dropped callback stub", e);
        }
    }

    static void foreignFutureDroppedCallback(long handle) {
        var futureWithHandler = uniffiForeignFutureHandleMap.remove(handle);
        futureWithHandler.cancel();
    }

    // For testing
    public static int uniffiForeignFutureHandleCount() {
      return uniffiForeignFutureHandleMap.size();
    }
    {%- endif %}

    private static java.lang.String uniffiStackTraceToString(java.lang.Throwable e) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            return sw.toString();
        } catch (java.lang.Throwable _t) {
            return e.toString();
        }
    }
}


