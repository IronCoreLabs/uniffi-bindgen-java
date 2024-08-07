import uniffi.fixture.futures.*;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestFixtureCancelDelay {
  private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  // emulating Kotlin's `delay` non-blocking sleep
  public static CompletableFuture<Void> delay(long milliseconds) {
    CompletableFuture<Void> f = new CompletableFuture<>();
    scheduler.schedule(() -> f.complete(null), milliseconds, TimeUnit.MILLISECONDS);
    return f;
  }
  
  // runnable but rethrowing the exceptions CompletableFuture execution throws
  public interface FutureRunnable {
    void run() throws InterruptedException, ExecutionException;
  }
  
  public static long measureTimeMillis(FutureRunnable r) {
    long startTimeNanos = System.nanoTime();
    try {
      r.run();
    } catch (Exception e) {
      assert false : "unexpected future run failure";
    }
    long endTimeNanos = System.nanoTime();
    long elapsedTimeMillis = (endTimeNanos - startTimeNanos) / 1_000_000;

    return elapsedTimeMillis;
  }

  public static void assertReturnsImmediately(long actualTime, String testName) {
    // TODO(java): 4ms limit in Kotlin
    assert actualTime <= 15 : MessageFormat.format("unexpected {0} time: {1}ms", testName, actualTime);
  }
  
  public static void assertApproximateTime(long actualTime, int expectedTime, String testName) {
    assert actualTime >= expectedTime && actualTime <= expectedTime + 100 : MessageFormat.format("unexpected {0} time: {1}ms", testName, actualTime);
  }

  public static void main(String[] args) throws Exception {
    try {
      // init UniFFI to get good measurements after that
      {
        var time = measureTimeMillis(() -> {
            Futures.alwaysReady().get();
        });

        System.out.println(MessageFormat.format("init time: {0}ms", time));
      }

      // Test `always_ready`
      {
        var time = measureTimeMillis(() -> {
            var result = Futures.alwaysReady().get();
            assert result.equals(true);
        });

        assertReturnsImmediately(time, "always_ready");
      }

      // Test foreign implemented async trait methods
      {
        class JavaAsyncParser implements AsyncParser {
          int completedDelays = 0;

          @Override
          public CompletableFuture<String> asString(Integer delayMs, Integer value) {
            return TestFixtureCancelDelay.delay((long)delayMs).thenApply(nothing -> {
              return value.toString();
            });
          }

          @Override
          public CompletableFuture<Integer> tryFromString(Integer delayMs, String value) {
            return TestFixtureCancelDelay.delay((long)delayMs).thenCompose((Void nothing) -> {
              CompletableFuture<Integer> f = new CompletableFuture<>();
              if (value.equals("force-unexpected-exception")) {
                f.completeExceptionally(new RuntimeException("UnexpectedException"));
                return f;
              }
              try {
                f.complete(Integer.parseInt(value));
              } catch (NumberFormatException e) {
                f.completeExceptionally(new ParserException.NotAnInt());
              }
              return f;
            });
          }

          @Override
          public CompletableFuture<Void> delay(Integer delayMs) {
            System.out.println("Delay in Java trait impl called: " + System.nanoTime());
            return TestFixtureCancelDelay.delay((long)delayMs).thenRun(() -> {
              System.out.println("Delay in Java trait impl finished executing: " + System.nanoTime());
              completedDelays += 1;
            });
          }

          @Override
          public CompletableFuture<Void> tryDelay(String delayMs) {
            try {
              var parsed = Long.parseLong(delayMs);
              return TestFixtureCancelDelay.delay(parsed).thenRun(() -> {
                completedDelays += 1;
              });
            } catch (NumberFormatException e) {
              var f = new CompletableFuture<Void>();
              f.completeExceptionally(new ParserException.NotAnInt());
              return f;
            }
          }
        }

        var traitObj = new JavaAsyncParser();
        var completedDelaysBefore = traitObj.completedDelays;
        System.out.println("Calling for cancel_delay from Java: " + System.nanoTime());
        Futures.cancelDelayUsingTrait(traitObj, 50);
        // sleep long enough so that the `delay()` call would finish if it wasn't cancelled.
        TestFixtureCancelDelay.delay(500).get();
        // If the task was cancelled, then completedDelays won't have increased
        assert traitObj.completedDelays == completedDelaysBefore : MessageFormat.format("{0} current delays != {1} delays before", traitObj.completedDelays, completedDelaysBefore);

        // Test that all handles were cleaned up
        // TODO(murph): this is inconsistently failing in CI, touch
        var endingHandleCount = UniffiAsyncHelpers.uniffiForeignFutureHandleCount();
        assert endingHandleCount == 0 : MessageFormat.format("{0} current handle count != 0", endingHandleCount);
      }
    } finally {
      // bring down the scheduler, if it's not shut down it'll hold the main thread open.
      scheduler.shutdown();
    }
  }
}
