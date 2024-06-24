import uniffi.fixture.futures.*;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class TestFixtureFutures {
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
    assert actualTime <= 4 : MessageFormat.format("unexpected {0} time: {1}ms", testName, actualTime);
  }
  
  public static void assertApproximateTime(long actualTime, int expectedTime, String testName) {
    assert actualTime >= expectedTime && actualTime <= expectedTime + 100 : MessageFormat.format("unexpected {0} time: {1}ms", testName, actualTime);
  }

  public static void main(String[] args) throws Exception {
    var time = measureTimeMillis(() -> {
        Futures.alwaysReady().get();
    });

    System.out.println(MessageFormat.format("init time: {0}ms", time));

    time = measureTimeMillis(() -> {
        var result = Futures.alwaysReady().get();
        assert result.equals(true);
    });

    assertReturnsImmediately(time, "always_ready");
  }
}
