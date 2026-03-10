package {{ config.package_name() }};

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

/**
 * Serializer for the FFI buffer protocol.
 *
 * Each FfiBufferElement is an 8-byte slot. Arguments are serialized sequentially,
 * followed by space for the return value and RustCallStatus (4 slots).
 *
 * Buffer layout: [arg1...][arg2...]...[return_value][RustCallStatus]
 *
 * RustCallStatus occupies 4 slots: 1 slot for the status code byte,
 * plus 3 slots for the error RustBuffer (capacity, len, data).
 */
final class FfiSerializer {
    private static final int SLOT_SIZE = 8;

    private final Memory memory;
    private int writeOffset;
    private int readOffset;

    /**
     * Allocate a buffer with the given total number of 8-byte slots.
     */
    public FfiSerializer(int totalSlots) {
        this.memory = new Memory((long) totalSlots * SLOT_SIZE);
        this.memory.clear();
        this.writeOffset = 0;
        this.readOffset = 0;
    }

    /** Get the underlying pointer for passing to JNA. */
    public Pointer getPointer() {
        return this.memory;
    }

    /** Set the read cursor to a specific slot offset. */
    public void setReadOffset(int slot) {
        this.readOffset = slot * SLOT_SIZE;
    }

    // --- Write methods (each writes to one 8-byte slot and advances) ---

    public void writeByte(byte value) {
        memory.setLong(writeOffset, value & 0xFFL);
        writeOffset += SLOT_SIZE;
    }

    public void writeShort(short value) {
        memory.setLong(writeOffset, value & 0xFFFFL);
        writeOffset += SLOT_SIZE;
    }

    public void writeInt(int value) {
        memory.setLong(writeOffset, value & 0xFFFFFFFFL);
        writeOffset += SLOT_SIZE;
    }

    public void writeLong(long value) {
        memory.setLong(writeOffset, value);
        writeOffset += SLOT_SIZE;
    }

    public void writeFloat(float value) {
        writeInt(Float.floatToRawIntBits(value));
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToRawLongBits(value));
    }

    public void writePointer(Pointer value) {
        writeLong(Pointer.nativeValue(value));
    }

    /**
     * Write a RustBuffer as 3 slots: capacity, len, data pointer.
     */
    public void writeRustBuffer(RustBuffer.ByValue buf) {
        writeLong(buf.capacity);
        writeLong(buf.len);
        writeLong(Pointer.nativeValue(buf.data));
    }

    // --- Read methods (each reads from one 8-byte slot and advances) ---

    public byte readByte() {
        long value = memory.getLong(readOffset);
        readOffset += SLOT_SIZE;
        return (byte) value;
    }

    public short readShort() {
        long value = memory.getLong(readOffset);
        readOffset += SLOT_SIZE;
        return (short) value;
    }

    public int readInt() {
        long value = memory.getLong(readOffset);
        readOffset += SLOT_SIZE;
        return (int) value;
    }

    public long readLong() {
        long value = memory.getLong(readOffset);
        readOffset += SLOT_SIZE;
        return value;
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readLong());
    }

    public Pointer readPointer() {
        long value = readLong();
        return new Pointer(value);
    }

    /**
     * Read a RustBuffer from 3 slots: capacity, len, data pointer.
     */
    public RustBuffer.ByValue readRustBuffer() {
        long capacity = readLong();
        long len = readLong();
        long dataPtr = readLong();
        RustBuffer.ByValue buf = new RustBuffer.ByValue();
        buf.capacity = capacity;
        buf.len = len;
        buf.data = new Pointer(dataPtr);
        return buf;
    }

    /**
     * Read a RustCallStatus from the buffer (4 slots):
     * 1 slot for the code byte, 3 slots for the error RustBuffer.
     */
    public UniffiRustCallStatus readCallStatus() {
        byte code = readByte();
        RustBuffer.ByValue errorBuf = readRustBuffer();
        return UniffiRustCallStatus.create(code, errorBuf);
    }
}
