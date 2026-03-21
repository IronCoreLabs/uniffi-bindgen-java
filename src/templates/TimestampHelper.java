package {{ config.package_name() }};

public enum FfiConverterTimestamp implements FfiConverterRustBuffer<java.time.Instant> {
    INSTANCE;

    @Override
    public java.time.Instant read(java.nio.ByteBuffer buf) {
        long seconds = buf.getLong();
        // Type mismatch (should be u32) but we check for overflow/underflow below
        long nanoseconds = (long) buf.getInt();
        if (nanoseconds < 0) {
            throw new java.time.DateTimeException("Instant nanoseconds exceed minimum or maximum supported by uniffi");
        }
        if (seconds >= 0) {
            return java.time.Instant.EPOCH.plus(java.time.Duration.ofSeconds(seconds, nanoseconds));
        } else {
            return java.time.Instant.EPOCH.minus(java.time.Duration.ofSeconds(-seconds, nanoseconds));
        }
    }

    // 8 bytes for seconds, 4 bytes for nanoseconds
    @Override
    public long allocationSize(java.time.Instant value) {
        return 12L;
    }

    @Override
    public void write(java.time.Instant value, java.nio.ByteBuffer buf) {
        java.time.Duration epochOffset = java.time.Duration.between(java.time.Instant.EPOCH, value);

        var sign = 1;
        if (epochOffset.isNegative()) {
            sign = -1;
            epochOffset = epochOffset.negated();
        }

        if (epochOffset.getNano() < 0) {
            // Java docs provide guarantee that nano will always be positive, so this should be impossible
            // See: https://docs.oracle.com/javase/8/docs/api/java/time/Instant.html
            throw new java.lang.IllegalArgumentException("Invalid timestamp, nano value must be non-negative");
        }

        buf.putLong(sign * epochOffset.getSeconds());
        // Type mismatch (should be u32) but since values will always be between 0 and 999,999,999 it should be OK
        buf.putInt(epochOffset.getNano());
    }
}
