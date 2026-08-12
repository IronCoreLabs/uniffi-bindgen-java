/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import java.nio.ByteBuffer;
import uniffi.zero_copy.*;

public class TestZeroCopy {
    static ByteBuffer direct(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes).flip();
        return buf;
    }

    public static void main(String[] args) {
        byte[] bytes = new byte[]{1, 2, 3, 4, 5};

        assert ZeroCopy.checksumBorrowed(direct(bytes)) == 15 : "borrowed checksum should be 15";
        assert ZeroCopy.lenBorrowed(direct(bytes)) == 5 : "borrowed len should be 5";
        assert ZeroCopy.firstByteBorrowed(direct(bytes)) == 1 : "borrowed first byte should be 1";

        assert ZeroCopy.checksumOwned(bytes) == ZeroCopy.checksumBorrowed(direct(bytes))
            : "owned and borrowed checksums should agree";

        ByteBuffer reused = direct(bytes);
        assert ZeroCopy.firstByteBorrowed(reused) == 1 : "expected original first byte";
        reused.put(0, (byte) 99);
        assert ZeroCopy.firstByteBorrowed(reused) == 99 : "mutation should be visible to Rust";
        reused.put(0, (byte) 1);

        ByteBuffer sliced = direct(bytes);
        sliced.position(2);
        assert ZeroCopy.lenBorrowed(sliced) == 3 : "should borrow only the remaining bytes";
        sliced.position(2);
        assert ZeroCopy.checksumBorrowed(sliced) == 12 : "3+4+5 = 12";
        sliced.position(2);
        assert ZeroCopy.firstByteBorrowed(sliced) == 3 : "slice should start at index 2";

        byte[] tail = new byte[]{6, 7};
        assert java.util.Arrays.equals(
            ZeroCopy.concatBorrowedAndOwned(direct(bytes), tail),
            new byte[]{1, 2, 3, 4, 5, 6, 7}) : "borrowed and owned args should keep their order";

        // Empty lowers to (null, 0), which Rust reads as an empty slice rather than crashing.
        assert ZeroCopy.lenBorrowed(ByteBuffer.allocateDirect(0)) == 0 : "empty buffer has len 0";
        assert ZeroCopy.checksumBorrowed(ByteBuffer.allocateDirect(0)) == 0 : "empty buffer sums to 0";
        assert ZeroCopy.firstByteBorrowed(ByteBuffer.allocateDirect(0)) == 0 : "empty buffer has no first byte";

        // Lowering keys off `remaining`, not `capacity`, so a drained buffer takes the same
        // (null, 0) path while its backing store is still very much alive.
        ByteBuffer drained = direct(bytes);
        drained.position(drained.limit());
        assert ZeroCopy.lenBorrowed(drained) == 0 : "drained buffer has len 0";
        drained.position(drained.limit());
        assert ZeroCopy.checksumBorrowed(drained) == 0 : "drained buffer sums to 0";

        // A null pointer in argument position must not disturb the argument that follows it.
        assert java.util.Arrays.equals(
            ZeroCopy.concatBorrowedAndOwned(ByteBuffer.allocateDirect(0), tail),
            tail) : "empty borrowed arg should leave the owned arg intact";

        // A heap buffer has no stable native address, so it must be rejected rather than
        // silently lowered as a null pointer.
        boolean threw = false;
        try {
            ZeroCopy.checksumBorrowed(ByteBuffer.wrap(bytes));
        } catch (IllegalArgumentException e) {
            threw = true;
            assert e.getMessage().contains("direct ByteBuffer") : "message should say what to do";
        }
        assert threw : "heap ByteBuffer should be rejected";

        // Larger payload, exercising the slab across many lowerings.
        byte[] big = new byte[64 * 1024];
        for (int i = 0; i < big.length; i++) big[i] = (byte) (i & 0x7F);
        long want = 0;
        for (byte b : big) want += (b & 0xFF);
        ByteBuffer bigBuf = direct(big);
        for (int i = 0; i < 10_000; i++) {
            bigBuf.rewind();
            assert ZeroCopy.checksumBorrowed(bigBuf) == want : "large borrowed checksum mismatch";
        }
    }
}
