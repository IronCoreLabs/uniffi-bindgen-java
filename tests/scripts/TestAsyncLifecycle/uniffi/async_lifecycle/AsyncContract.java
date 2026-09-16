package uniffi.async_lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Asserts the handle contract uniffi_core documents: free exactly once, and no poll, complete or
// cancel on a handle after it has been freed.
public final class AsyncContract {
    static final class Ledger {
        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger completes = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger frees = new AtomicInteger();
        volatile boolean freed;
        // Pipeline stage in progress: at most one thread, re-entrant on an inline executor.
        volatile Thread inFlight;
        int depth;
    }

    // Handle values are heap addresses and are reused after free, so ledgers are per call.
    static final ConcurrentLinkedQueue<Ledger> ledgers = new ConcurrentLinkedQueue<>();
    static final ConcurrentLinkedQueue<String> violations = new ConcurrentLinkedQueue<>();

    static void violate(String what, long handle) {
        violations.add(what + " (handle " + Long.toHexString(handle) + ", " + Thread.currentThread().getName() + ")");
    }

    // Returns whether the native call may proceed; a call on a freed handle is skipped.
    static boolean enter(Ledger l, String stage, long handle) {
        boolean live = !l.freed;
        if (!live) {
            violate(stage + " after free", handle);
        }
        Thread me = Thread.currentThread();
        synchronized (l) {
            if (l.inFlight != null && l.inFlight != me) {
                violate(stage + " concurrent with another pipeline stage", handle);
            }
            l.inFlight = me;
            l.depth++;
        }
        return live;
    }

    static void exit(Ledger l) {
        synchronized (l) {
            if (--l.depth == 0) {
                l.inFlight = null;
            }
        }
    }

    static CompletableFuture<Void> call(long handle, Executor executor) {
        Ledger l = new Ledger();
        ledgers.add(l);
        return UniffiAsyncHelpers.uniffiRustCallAsync(
            executor,
            handle,
            (future, callback, continuation) -> {
                boolean live = enter(l, "poll", future);
                try {
                    l.polls.incrementAndGet();
                    // Native calls are serialized per handle only after the overlap has been
                    // recorded, so a violation is reported instead of executed.
                    synchronized (l) {
                        if (live && !l.freed) {
                            UniffiLib.ffi_uniffi_fixture_async_lifecycle_rust_future_poll_void(future, callback, continuation);
                        } else {
                            // Keeps the pipeline draining so the run ends and reports.
                            UniffiAsyncHelpers.continuationCallback(continuation, UniffiAsyncHelpers.UNIFFI_RUST_FUTURE_POLL_READY);
                        }
                    }
                } finally {
                    exit(l);
                }
            },
            (_allocator, future, status) -> {
                boolean live = enter(l, "complete", future);
                try {
                    if (l.completes.incrementAndGet() > 1) {
                        violate("complete twice", future);
                        live = false;
                    }
                    synchronized (l) {
                        if (live && !l.freed) {
                            UniffiLib.ffi_uniffi_fixture_async_lifecycle_rust_future_complete_void(future, status);
                        }
                    }
                } finally {
                    exit(l);
                }
            },
            (future) -> {
                l.cancels.incrementAndGet();
                synchronized (l) {
                    if (l.freed) {
                        violate("cancel after free", future);
                        return;
                    }
                    UniffiLib.ffi_uniffi_fixture_async_lifecycle_rust_future_cancel_void(future);
                }
            },
            (future) -> {
                if (l.frees.incrementAndGet() > 1) {
                    violate("free twice", future);
                    return;
                }
                if (l.inFlight != null && l.inFlight != Thread.currentThread()) {
                    violate("free while a pipeline stage is in flight on another thread", future);
                }
                synchronized (l) {
                    l.freed = true;
                    UniffiLib.ffi_uniffi_fixture_async_lifecycle_rust_future_free_void(future);
                }
            },
            () -> {},
            new UniffiNullRustCallStatusErrorHandler()
        );
    }

    static long newHandle(int kind) {
        return switch (kind % 3) {
            case 0 -> UniffiLib.uniffi_uniffi_fixture_async_lifecycle_fn_func_tracked_ready();
            case 1 -> UniffiLib.uniffi_uniffi_fixture_async_lifecycle_fn_func_tracked_sleep((short) 1);
            default -> UniffiLib.uniffi_uniffi_fixture_async_lifecycle_fn_func_tracked_wake_storm((short) 1, (short) 2);
        };
    }

    // Cancel timings vary so cancel lands at every pipeline stage.
    static void storm(String name, Executor executor, int threads, int iters, ScheduledExecutorService canceller) throws Exception {
        ledgers.clear();
        violations.clear();
        List<Thread> workers = new ArrayList<>();
        List<CompletableFuture<Void>> futures = new java.util.concurrent.CopyOnWriteArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Thread w = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < iters; i++) {
                    var f = call(newHandle(i + seed), executor);
                    futures.add(f);
                    switch (rnd.nextInt(4)) {
                        case 0 -> {}
                        case 1 -> f.cancel(true);
                        case 2 -> {
                            Thread.yield();
                            f.cancel(true);
                        }
                        default -> canceller.schedule(() -> f.cancel(true), rnd.nextInt(3), TimeUnit.MILLISECONDS);
                    }
                }
            }, name + "-worker-" + t);
            workers.add(w);
            w.start();
        }
        for (Thread w : workers) {
            w.join();
        }
        for (var f : futures) {
            try {
                f.get(10, TimeUnit.SECONDS);
            } catch (java.util.concurrent.CancellationException | java.util.concurrent.ExecutionException expected) {
            }
        }
        // free runs after the future completes.
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (ledgers.stream().anyMatch(l -> l.frees.get() == 0)) {
            if (System.nanoTime() > deadline) {
                long leaked = ledgers.stream().filter(l -> l.frees.get() == 0).count();
                throw new AssertionError(name + ": " + leaked + " handles never freed");
            }
            Thread.sleep(5);
        }
        if (!violations.isEmpty()) {
            throw new AssertionError(name + ": " + violations.size() + " contract violations, first: " + violations.peek());
        }
        System.out.println("async handle contract [" + name + "] (" + ledgers.size() + " futures) ... ok");
    }

    public static void run() throws Exception {
        ScheduledExecutorService canceller = Executors.newScheduledThreadPool(2);
        ExecutorService single = Executors.newSingleThreadExecutor();
        ScheduledExecutorService jitterPool = Executors.newScheduledThreadPool(4);
        Executor jittery = r -> jitterPool.schedule(r, ThreadLocalRandom.current().nextInt(3), TimeUnit.MILLISECONDS);
        Executor rejecting = r -> { throw new RejectedExecutionException("rejecting executor"); };
        try {
            storm("common pool", java.util.concurrent.ForkJoinPool.commonPool(), 8, 5_000, canceller);
            storm("inline executor", Runnable::run, 8, 2_000, canceller);
            storm("single thread executor", single, 4, 2_000, canceller);
            storm("jittery executor", jittery, 4, 500, canceller);
            storm("rejecting executor", rejecting, 4, 500, canceller);
        } finally {
            canceller.shutdownNow();
            single.shutdownNow();
            jitterPool.shutdownNow();
        }
    }
}
