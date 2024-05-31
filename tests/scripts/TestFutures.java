import uniffi.futures.*;

import java.util.concurrent.CompletableFuture;

public class TestFutures {
  public static void main(String[] args) throws Exception {
    CompletableFuture<String> future = Futures.sayAfter(20, "Alice");

    future.thenAccept(result -> {
      assert result.equals("Hello, Alice!");
    });

    future.get();
  }
}
