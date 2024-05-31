package {{ config.package_name() }};

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.Duration;
import java.time.DateTimeException;

public enum FfiConverterDuration implements FfiConverterRustBuffer<Duration> {
    INSTANCE;
    
    @Override
    public Duration read(ByteBuffer buf) {
        // Type mismatch (should be u64) but we check for overflow/underflow below
        long seconds = buf.getLong();
        // Type mismatch (should be u32) but we check for overflow/underflow below
        long nanoseconds = (long) buf.getInt();
        if (seconds < 0) {
            throw new DateTimeException("Duration exceeds minimum or maximum value supported by uniffi");
        }
        if (nanoseconds < 0) {
            throw new DateTimeException("Duration nanoseconds exceed minimum or maximum supported by uniffi");
        }
        return Duration.ofSeconds(seconds, nanoseconds);
    }

    // 8 bytes for seconds, 4 bytes for nanoseconds
    @Override
    public long allocationSize(Duration value) {
        return 12L;
    }

    @Override
    public void write(Duration value, ByteBuffer buf) {
        if (value.getSeconds() < 0) {
            // Rust does not support negative Durations
            throw new IllegalArgumentException("Invalid duration, must be non-negative");
        }

        if (value.getNano() < 0) {
            // Java docs provide guarantee that nano will always be positive, so this should be impossible
            // See: https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html
            throw new IllegalArgumentException("Invalid duration, nano value must be non-negative");
        }

        // Type mismatch (should be u64) but since Rust doesn't support negative durations we should be OK
        buf.putLong(value.getSeconds());
        // Type mismatch (should be u32) but since values will always be between 0 and 999,999,999 it should be OK
        buf.putInt(value.getNano());
    }
}
