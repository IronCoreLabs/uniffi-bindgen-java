package {{ config.package_name() }};

public enum FfiConverterString implements FfiConverter<java.lang.String, RustBuffer.ByValue> {
    INSTANCE;

    // Note: we don't inherit from FfiConverterRustBuffer, because we use a
    // special encoding when lowering/lifting.  We can use `RustBuffer.len` to
    // store our length and avoid writing it out to the buffer.
    @Override
    public java.lang.String lift(RustBuffer.ByValue value) {
        try {
            byte[] byteArr = new byte[(int) value.len];
            value.asByteBuffer().get(byteArr);
            return new java.lang.String(byteArr, java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            RustBuffer.free(value);
        }
    }

    @Override
    public java.lang.String read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        byte[] byteArr = new byte[len];
        buf.get(byteArr);
        return new java.lang.String(byteArr, java.nio.charset.StandardCharsets.UTF_8);
    }

    private java.nio.ByteBuffer toUtf8(java.lang.String value) {
        // Make sure we don't have invalid UTF-16, check for lone surrogates.
        java.nio.charset.CharsetEncoder encoder = java.nio.charset.StandardCharsets.UTF_8.newEncoder();
        encoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
        try {
            return encoder.encode(java.nio.CharBuffer.wrap(value));
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new java.lang.RuntimeException(e);
        }
    }

    @Override
    public RustBuffer.ByValue lower(java.lang.String value) {
        java.nio.ByteBuffer byteBuf = toUtf8(value);
        // Ideally we'd pass these bytes to `ffi_bytebuffer_from_bytes`, but doing so would require us
        // to copy them into a JNA `Memory`. So we might as well directly copy them into a `RustBuffer`.
        RustBuffer.ByValue rbuf = RustBuffer.alloc((long) byteBuf.limit());
        rbuf.asByteBuffer().put(byteBuf);
        return rbuf;
    }

    // We aren't sure exactly how many bytes our string will be once it's UTF-8
    // encoded.  Allocate 3 bytes per UTF-16 code unit which will always be
    // enough.
    @Override
    public long allocationSize(java.lang.String value) {
        long sizeForLength = 4L;
        long sizeForString = (long) value.length() * 3L;
        return sizeForLength + sizeForString;
    }

    @Override
    public void write(java.lang.String value, java.nio.ByteBuffer buf) {
        java.nio.ByteBuffer byteBuf = toUtf8(value);
        buf.putInt(byteBuf.limit());
        buf.put(byteBuf);
    }
}


