import uniffi.fixture.futures.*;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

public class TestFixtureFutures {
  public static long measureTimeMillis(Runnable r) {
    long startTimeNanos = System.nanoTime();
    r.run();
    long endTimeNanos = System.nanoTime();
    long elapsedTimeMillis = (endTimeNanos - startTimeNanos) / 1_000_000;

    return elapsedTimeMillis;
  }

  public static void main(String[] args) throws Exception {
    var time = measureTimeMillis(() -> {
      try {
        Futures.alwaysReady().get();
      } catch (Exception e) {
        assert false : "always_ready future should not be interrupted.";   
      }
    });

    System.out.println(MessageFormat.format("init time: {0}ms", time));
  }
}
