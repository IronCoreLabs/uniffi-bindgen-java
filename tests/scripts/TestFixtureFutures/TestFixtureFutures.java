import uniffi.fixture.futures.*;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TestFixtureFutures {
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
  
  static long nano_to_millis = 1_000_000;
  public static long measureTimeMillis(FutureRunnable r) {
    long startTimeNanos = System.nanoTime();
    try {
      r.run();
    } catch (Exception e) {
      assert false : "unexpected future run failure";
    }
    long endTimeNanos = System.nanoTime();
    long elapsedTimeMillis = (endTimeNanos - startTimeNanos) / nano_to_millis;

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

      // Test `void`.
      {
        var time = measureTimeMillis(() -> {
          var result = Futures._void().get();

          assert result == null;
        });

        assertReturnsImmediately(time, "void");
      }

      // Test `sleep`.
      {
        var time = measureTimeMillis(() -> {
          Futures.sleep((short)200).get();
        });

        assertApproximateTime(time, 200, "sleep");
      }
    
      // Test sequential futures.
      {
        var time = measureTimeMillis(() -> {
          var aliceResult = Futures.sayAfter((short)100, "Alice").get();
          var bobResult = Futures.sayAfter((short)200, "Bob").get();

          assert aliceResult.equals("Hello, Alice!");
          assert bobResult.equals("Hello, Bob!");
        });

        assertApproximateTime(time, 300, "sequential future");
      }

      // Test concurrent futures.
      {
        var time = measureTimeMillis(() -> {
          var alice = Futures.sayAfter((short)100, "Alice");
          var bob = Futures.sayAfter((short)200, "Bob");

          assert alice.get().equals("Hello, Alice!");
          assert bob.get().equals("Hello, Bob!");
        });
    
        assertApproximateTime(time, 200, "concurrent future");
      }

      // Test async methods.
      {
        var megaphone = Futures.newMegaphone();
        var time = measureTimeMillis(() -> {
          var resultAlice = megaphone.sayAfter((short)200, "Alice").get();

          assert resultAlice.equals("HELLO, ALICE!");
        });

        assertApproximateTime(time, 200, "async methods");
      }

      {
        var megaphone = Futures.newMegaphone();
        var time = measureTimeMillis(() -> {
          var resultAlice = Futures.sayAfterWithMegaphone(megaphone, (short)200, "Alice").get();

          assert resultAlice.equals("HELLO, ALICE!");
        });

        assertApproximateTime(time, 200, "async methods");
      }

      // Test async constructors
      {
        var megaphone = Megaphone.secondary().get();
        assert megaphone.sayAfter((short)1, "hi").get().equals("HELLO, HI!");
      }

      // Test async method returning optional object
      {
        var megaphone = Futures.asyncMaybeNewMegaphone(true).get();
        assert megaphone != null;
    
        var not_megaphone = Futures.asyncMaybeNewMegaphone(false).get();
        assert not_megaphone == null;
      }

      // Test async methods in trait interfaces
      {
        var traits = Futures.getSayAfterTraits();
        var time = measureTimeMillis(() -> {
          var result1 = traits.get(0).sayAfter((short)100, "Alice").get();
          var result2 = traits.get(1).sayAfter((short)100, "Bob").get();

          assert result1.equals("Hello, Alice!");
          assert result2.equals("Hello, Bob!");
        });

        assertApproximateTime(time, 200, "async trait methods");
      }

      // Test async methods in UDL-defined trait interfaces
      {
        var traits = Futures.getSayAfterUdlTraits();
        var time = measureTimeMillis(() -> {
          var result1 = traits.get(0).sayAfter((short)100, "Alice").get();
          var result2 = traits.get(1).sayAfter((short)100, "Bob").get();

          assert result1.equals("Hello, Alice!");
          assert result2.equals("Hello, Bob!");
        });

        assertApproximateTime(time, 200, "async UDL methods");
      }

      // Test foreign implemented async trait methods
      {
        class JavaAsyncParser implements AsyncParser {
          int completedDelays = 0;

          @Override
          public CompletableFuture<String> asString(Integer delayMs, Integer value) {
            return TestFixtureFutures.delay((long)delayMs).thenApply(nothing -> {
              return value.toString();
            });
          }

          @Override
          public CompletableFuture<Integer> tryFromString(Integer delayMs, String value) {
            return TestFixtureFutures.delay((long)delayMs).thenCompose((Void nothing) -> {
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
            return TestFixtureFutures.delay((long)delayMs).thenRun(() -> {
              completedDelays += 1;
            });
          }

          @Override
          public CompletableFuture<Void> tryDelay(String delayMs) {
            try {
              var parsed = Long.parseLong(delayMs);
              return TestFixtureFutures.delay(parsed).thenRun(() -> {
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
        assert Futures.asStringUsingTrait(traitObj, 1, 42).get().equals("42");
        assert Futures.tryFromStringUsingTrait(traitObj, 1, "42").get().equals(42);
        try {
          Futures.tryFromStringUsingTrait(traitObj, 1, "fourty-two").get();
          throw new RuntimeException("Expected last statement to throw");
        } catch (ExecutionException e) {
          if (e.getCause() instanceof ParserException.NotAnInt) {
              // Expected
          } else {
            throw e;
          }
        }
        try {
          Futures.tryFromStringUsingTrait(traitObj, 1, "force-unexpected-exception").get();
          throw new RuntimeException("Expected last statement to throw");
        } catch (ExecutionException e) {
          if (e.getCause() instanceof ParserException.UnexpectedException) {
             // Expected
          } else {
             throw e;
          }
        }
        Futures.delayUsingTrait(traitObj, 1).get();
        try {
          Futures.tryDelayUsingTrait(traitObj, "one").get();
          throw new RuntimeException("Expected last statement to throw");
        } catch (ExecutionException e) {
          if (e.getCause() instanceof ParserException.NotAnInt) {
            // Expected
          } else {
            throw e;
          }
        }
        var completedDelaysBefore = traitObj.completedDelays;
        Futures.cancelDelayUsingTrait(traitObj, 10).get();
        // sleep long enough so that the `delay()` call would finish if it wasn't cancelled.
        TestFixtureFutures.delay(100).get();
        // If the task was cancelled, then completedDelays won't have increased
        assert traitObj.completedDelays == completedDelaysBefore : MessageFormat.format("{0} current delays != {1} delays before", traitObj.completedDelays, completedDelaysBefore);

        // Test that all handles were cleaned up
        var endingHandleCount = UniffiAsyncHelpers.uniffiForeignFutureHandleCount();
        assert endingHandleCount == 0 : MessageFormat.format("{0} current handle count != 0", endingHandleCount);
      }

      // Test with the Tokio runtime.
      {
        var time = measureTimeMillis(() -> {
          var resultAlice = Futures.sayAfterWithTokio((short)200, "Alice").get();

          assert resultAlice.equals("Hello, Alice (with Tokio)!");
        });

        assertApproximateTime(time, 200, "with tokio runtime");
      }

      // Test fallible function/method
      {
        var time1 = measureTimeMillis(() -> {
          try {
            Futures.fallibleMe(false).get();
            assert true;
          } catch (Exception e) {
            assert false; // should never be reached
          }
        });

        System.out.print(MessageFormat.format("fallible function (with result): {0}ms", time1));
        assert time1 < 100;
        System.out.println(" ... ok");

        var time2 = measureTimeMillis(() -> {
          try {
            Futures.fallibleMe(true).get();
            assert false; // should never be reached
          } catch (Exception e) {
            assert true;
          }
        });

        System.out.print(MessageFormat.format("fallible function (with exception): {0}ms", time2));
        assert time2 < 100;
        System.out.println(" ... ok");

        var megaphone = Futures.newMegaphone();

        var time3 = measureTimeMillis(() -> {
          try {
            megaphone.fallibleMe(false).get();
            assert true;
          } catch (Exception e) {
            assert false; // should never be reached
          }
        });

        System.out.print(MessageFormat.format("fallible method (with result): {0}ms", time3));
        assert time3 < 100;
        System.out.println(" ... ok");
        
        var time4 = measureTimeMillis(() -> {
          try {
            megaphone.fallibleMe(true).get();
            assert false; // should never be reached
          } catch (Exception e) {
            assert true;
          }
        });

        System.out.print(MessageFormat.format("fallible method (with exception): {0}ms", time4));
        assert time4 < 100;
        System.out.println(" ... ok");

        Futures.fallibleStruct(false).get();
        try {
          Futures.fallibleStruct(true).get();
          assert false; // should never be reached
        } catch (Exception e) {
          assert true;
        }
      }

      // Test record.
      {
        var time = measureTimeMillis(() -> {
          var result = Futures.newMyRecord("foo", 42).get();

          assert result.a().equals("foo");
          assert result.b() == 42;
        });

        System.out.print(MessageFormat.format("record: {0}ms", time));
        assert time < 100;
        System.out.println(" ... ok");
      }

      // Test a broken sleep.
      {
        var time = measureTimeMillis(() -> {
          Futures.brokenSleep((short)100, (short)0).get(); // calls the waker twice immediately
          Futures.sleep((short)100).get(); // wait for possible failure

          Futures.brokenSleep((short)100, (short)100).get(); // calls the waker a second time after 1s
          Futures.sleep((short)200).get(); // wait for possible failure
        });

        assertApproximateTime(time, 500, "broken sleep");
      }

      // Test a future that uses a lock and that is cancelled.
      {
        var time = measureTimeMillis(() -> {
          var job = Futures.useSharedResource(new SharedResourceOptions((short)5000, (short)100));

          // Wait some time to ensure the task has locked the shared resource
          TestFixtureFutures.delay(50).get();
          // Cancel the job before the shared resource has been released.
          job.cancel(true);

          // Try accessing the shared resource again. The initial task should release the shared resource before the
          // timeout expires.
          Futures.useSharedResource(new SharedResourceOptions((short)0, (short)1000)).get();
        });

        System.out.println(MessageFormat.format("useSharedResource: {0}ms", time));
      }

      // Test a future that uses a lock and that is not cancelled.
      {
        var time = measureTimeMillis(() -> {
          // spawn both at the same time so they contend for the resource
          var f1 = Futures.useSharedResource(new SharedResourceOptions((short)100, (short)1000));
          var f2 = Futures.useSharedResource(new SharedResourceOptions((short)0, (short)1000));

          f1.get();
          f2.get();
        });

        System.out.println(MessageFormat.format("useSharedResource (not cancelled): {0}ms", time));
      }
    } finally {
      // bring down the scheduler, if it's not shut down it'll hold the main thread open.
      scheduler.shutdown();
    }
  }
}
