import uniffi.async_lifecycle.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class TestAsyncLifecycle {
  // Only a leak exhausts the bound.
  static void awaitAllDropped(String label) throws Exception {
    long deadline = System.nanoTime() + 10_000_000_000L;
    while (AsyncLifecycle.droppedCount() != AsyncLifecycle.createdCount()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(label + ": created=" + AsyncLifecycle.createdCount()
            + " dropped=" + AsyncLifecycle.droppedCount());
      }
      Thread.sleep(5);
    }
  }

  public static void main(String[] args) throws Exception {
    {
      AsyncLifecycle.resetCounts();
      AsyncLifecycle.trackedReady().get();
      AsyncLifecycle.trackedSleep((short)1).get();
      AsyncLifecycle.trackedWakeStorm((short)1, (short)3).get();
      AsyncLifecycle.trackedSleep((short)50).cancel(true);
      AsyncLifecycle.trackedWakeStorm((short)50, (short)5).cancel(true);
      awaitAllDropped("single futures");
      assert AsyncLifecycle.createdCount() == 5 : "expected 5 created, got " + AsyncLifecycle.createdCount();
      System.out.println("drop accounting (5 futures) ... ok");
    }

    {
      AsyncLifecycle.resetCounts();
      int threads = 8, iters = 5_000;
      var failure = new AtomicReference<Throwable>();
      Thread[] workers = new Thread[threads];
      for (int t = 0; t < threads; t++) {
        final int seed = t;
        workers[t] = new Thread(() -> {
          try {
            for (int i = 0; i < iters; i++) {
              CompletableFuture<?> job = switch ((i + seed) % 3) {
                case 0 -> AsyncLifecycle.trackedSleep((short)1);
                case 1 -> AsyncLifecycle.trackedWakeStorm((short)1, (short)1);
                default -> AsyncLifecycle.trackedReady();
              };
              if (i % 2 == 0) {
                Thread.yield();
              }
              job.cancel(true);
            }
          } catch (Throwable e) {
            failure.compareAndSet(null, e);
          }
        });
        workers[t].start();
      }
      for (Thread w : workers) {
        w.join();
      }
      assert failure.get() == null : "cancel storm threw: " + failure.get();
      awaitAllDropped("cancel storm");
      assert AsyncLifecycle.createdCount() == (long) threads * iters;
      System.out.println("drop accounting after cancel storm (" + threads + " x " + iters + ") ... ok");
    }

    // cancel() from inside an async callback, i.e. on the executor thread while rust_future_poll
    // is on the stack. Freeing here would deadlock on the future's own Rust mutex.
    {
      AsyncLifecycle.resetCounts();
      var job = new AtomicReference<CompletableFuture<Void>>();
      Notifier cancelSelf = () -> {
        boolean cancelled = job.get().cancel(true);
        assert cancelled : "cancel from callback returned false";
        return CompletableFuture.completedFuture(null);
      };
      var f = AsyncLifecycle.sleepThenNotify((short)10, cancelSelf);
      job.set(f);
      try {
        f.get();
        throw new AssertionError("expected cancellation");
      } catch (java.util.concurrent.CancellationException expected) {
      }
      awaitAllDropped("cancel from callback");
      System.out.println("cancel from inside async callback ... ok");
    }

    uniffi.async_lifecycle.AsyncContract.run();
  }
}
