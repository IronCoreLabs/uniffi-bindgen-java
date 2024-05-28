import uniffi.chronological.*;

import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;

public class TestChronological {
  public static void main(String[] args) throws Exception {
    // Test passing timestamp and duration while returning timestamp
    assert Chronological.add(Instant.ofEpochSecond(100, 100), Duration.ofSeconds(1, 1))
            .equals(Instant.ofEpochSecond(101, 101));
            
    // Test passing timestamp while returning duration
    assert Chronological.diff(Instant.ofEpochSecond(101, 101), Instant.ofEpochSecond(100, 100))
            .equals(Duration.ofSeconds(1, 1));
            
    // Test pre-epoch timestamps
    assert Chronological.add(Instant.parse("1955-11-05T00:06:00.283000001Z"), Duration.ofSeconds(1, 1))
            .equals(Instant.parse("1955-11-05T00:06:01.283000002Z"));
            
    // Test exceptions are propagated
    try {
      Chronological.diff(Instant.ofEpochSecond(100), Instant.ofEpochSecond(101));
      throw new RuntimeException("Should have thrown a TimeDiffError exception!");
    } catch (ChronologicalException e) {
      // It's okay!
    }
    
    // Test max Instant upper bound
    assert Chronological.add(Instant.MAX, Duration.ofSeconds(0)).equals(Instant.MAX);

    // Test max Instant upper bound overflow
    try {
      Chronological.add(Instant.MAX, Duration.ofSeconds(1));
      throw new RuntimeException("Should have thrown a DateTimeException exception!");
    } catch (DateTimeException e) {
      // It's okay!
    }

    // Test that rust timestamps behave like Java timestamps
    // Unfortunately the JVM clock may be lower resolution than the Rust clock.
    // Sleep for 1ms between each call, which should ensure the JVM clock ticks
    // forward.
    var javaBefore = Instant.now();
    Thread.sleep(10);
    var rustNow = Chronological.now();
    Thread.sleep(10);
    var javaAfter = Instant.now();
    assert javaBefore.isBefore(rustNow);
    assert javaAfter.isAfter(rustNow);

    // Test optional values work
    assert Chronological.optional(Instant.MAX, Duration.ofSeconds(0));
    assert Chronological.optional(null, Duration.ofSeconds(0)) == false;
    assert Chronological.optional(Instant.MAX, null) == false;
  }
}
