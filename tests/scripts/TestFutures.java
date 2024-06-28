import uniffi.uniffi_example_futures.*;

import java.util.concurrent.CompletableFuture;

public class TestFutures {
  public static void main(String[] args) throws Exception {
    CompletableFuture<String> future = UniffiExampleFutures.sayAfter(20L, "Alice");

    future.thenAccept(result -> {
      assert result.equals("Hello, Alice!");
    });

    future.get();
  }
}
