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
        Futures.alwaysReady().get();
    });

    System.out.println(MessageFormat.format("init time: {0}ms", time));
  }
}
