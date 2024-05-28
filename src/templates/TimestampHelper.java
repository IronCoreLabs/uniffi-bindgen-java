package {{ config.package_name() }};

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.Duration;
import java.time.DateTimeException;

public enum FfiConverterTimestamp implements FfiConverterRustBuffer<Instant> {
    INSTANCE;

    @Override
    public Instant read(ByteBuffer buf) {
        long seconds = buf.getLong();
        // Type mismatch (should be u32) but we check for overflow/underflow below
        long nanoseconds = (long) buf.getInt();
        if (nanoseconds < 0) {
            throw new DateTimeException("Instant nanoseconds exceed minimum or maximum supported by uniffi");
        }
        if (seconds >= 0) {
            return Instant.EPOCH.plus(Duration.ofSeconds(seconds, nanoseconds));
        } else {
            return Instant.EPOCH.minus(Duration.ofSeconds(-seconds, nanoseconds));    
        }
    }

    // 8 bytes for seconds, 4 bytes for nanoseconds
    @Override
    public long allocationSize(Instant value) {
        return 12L;
    }

    @Override
    public void write(Instant value, ByteBuffer buf) {
        Duration epochOffset = Duration.between(Instant.EPOCH, value);

        var sign = 1;
        if (epochOffset.isNegative()) {
            sign = -1;
            epochOffset = epochOffset.negated();
        }

        if (epochOffset.getNano() < 0) {
            // Java docs provide guarantee that nano will always be positive, so this should be impossible
            // See: https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html
            throw new IllegalArgumentException("Invalid timestamp, nano value must be non-negative");
        }

        buf.putLong(sign * epochOffset.getSeconds());
        // Type mismatch (should be u32) but since values will always be between 0 and 999,999,999 it should be OK
        buf.putInt(epochOffset.getNano());
    }
}
