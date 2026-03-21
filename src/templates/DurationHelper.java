package {{ config.package_name() }};

public enum FfiConverterDuration implements FfiConverterRustBuffer<java.time.Duration> {
    INSTANCE;

    @Override
    public java.time.Duration read(java.nio.ByteBuffer buf) {
        // Type mismatch (should be u64) but we check for overflow/underflow below
        long seconds = buf.getLong();
        // Type mismatch (should be u32) but we check for overflow/underflow below
        long nanoseconds = (long) buf.getInt();
        if (seconds < 0) {
            throw new java.time.DateTimeException("Duration exceeds minimum or maximum value supported by uniffi");
        }
        if (nanoseconds < 0) {
            throw new java.time.DateTimeException("Duration nanoseconds exceed minimum or maximum supported by uniffi");
        }
        return java.time.Duration.ofSeconds(seconds, nanoseconds);
    }

    // 8 bytes for seconds, 4 bytes for nanoseconds
    @Override
    public long allocationSize(java.time.Duration value) {
        return 12L;
    }

    @Override
    public void write(java.time.Duration value, java.nio.ByteBuffer buf) {
        if (value.getSeconds() < 0) {
            // Rust does not support negative Durations
            throw new java.lang.IllegalArgumentException("Invalid duration, must be non-negative");
        }

        if (value.getNano() < 0) {
            // Java docs provide guarantee that nano will always be positive, so this should be impossible
            // See: https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html
            throw new java.lang.IllegalArgumentException("Invalid duration, nano value must be non-negative");
        }

        // Type mismatch (should be u64) but since Rust doesn't support negative durations we should be OK
        buf.putLong(value.getSeconds());
        // Type mismatch (should be u32) but since values will always be between 0 and 999,999,999 it should be OK
        buf.putInt(value.getNano());
    }
}
