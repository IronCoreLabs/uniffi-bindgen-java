/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.async_api_client.*;
import java.util.concurrent.CompletableFuture;

public class TestAsyncApiClient {
  public static void main(String[] args) throws Exception {
    CompletableFuture<String> future = UniffiExampleFutures.sayAfter(20L, "Alice");

    future.thenAccept(result -> {
      assert result.equals("Hello, Alice!");
    });

    future.get();
  }
}
