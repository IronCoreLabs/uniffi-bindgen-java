/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.byref_bytes.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class TestByRefBytes {
    public static void main(String[] args) {
        testRoundtrip();
        testEmpty();
        testPositionRespected();
        testMixedWithOwned();
        testHeapBufferRejected();

        System.out.println("All ByRef bytes tests passed!");
    }

    static ByteBuffer direct(byte... bytes) {
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes);
        buf.flip();
        return buf;
    }

    static void testRoundtrip() {
        ByteBuffer buf = direct((byte) 1, (byte) 2, (byte) 3, (byte) 250);
        assert ByrefBytes.sumBorrowed(buf) == 256 : "sumBorrowed failed";
        assert ByrefBytes.lenBorrowed(buf) == 4 : "lenBorrowed failed";
        assert Arrays.equals(ByrefBytes.copyBorrowed(buf), new byte[] { 1, 2, 3, (byte) 250 })
            : "copyBorrowed failed";
    }

    static void testEmpty() {
        ByteBuffer buf = ByteBuffer.allocateDirect(0);
        assert ByrefBytes.lenBorrowed(buf) == 0 : "empty lenBorrowed failed";
        assert ByrefBytes.copyBorrowed(buf).length == 0 : "empty copyBorrowed failed";
    }

    // Rust must see only the remaining bytes, not the whole buffer.
    static void testPositionRespected() {
        ByteBuffer buf = direct((byte) 10, (byte) 20, (byte) 30, (byte) 40);
        buf.position(2);
        assert ByrefBytes.lenBorrowed(buf) == 2 : "position-adjusted lenBorrowed failed";
        assert Arrays.equals(ByrefBytes.copyBorrowed(buf), new byte[] { 30, 40 })
            : "position-adjusted copyBorrowed failed";
    }

    static void testMixedWithOwned() {
        byte[] result = ByrefBytes.concatBorrowedAndOwned(
            direct((byte) 1, (byte) 2),
            new byte[] { 3, 4 });
        assert Arrays.equals(result, new byte[] { 1, 2, 3, 4 }) : "concatBorrowedAndOwned failed";
    }

    static void testHeapBufferRejected() {
        try {
            ByrefBytes.lenBorrowed(ByteBuffer.wrap(new byte[] { 1, 2, 3 }));
            throw new AssertionError("expected heap ByteBuffer to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
