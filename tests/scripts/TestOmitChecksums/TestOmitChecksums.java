import uniffi.arithmetic.*;

/**
 * Test that omit_checksums=true works correctly.
 *
 * This test verifies that bindings generated with omit_checksums=true
 * function correctly without API checksum verification. If the library
 * loads and basic operations work, the omit_checksums feature is working.
 */
public class TestOmitChecksums {
    public static void main(String[] args) throws Exception {
        // Basic arithmetic operations to verify the library works without checksums
        assert Arithmetic.add(2L, 4L) == 6L : "add(2, 4) should equal 6";
        assert Arithmetic.sub(4L, 2L) == 2L : "sub(4, 2) should equal 2";
        assert Arithmetic.div(8L, 4L) == 2L : "div(8, 4) should equal 2";
        assert Arithmetic.equal(2L, 2L) : "equal(2, 2) should be true";
        assert !Arithmetic.equal(2L, 3L) : "equal(2, 3) should be false";

        // Test error handling still works
        try {
            Arithmetic.sub(0L, 2L);
            assert false : "Subtraction causing negative should throw IntegerOverflow";
        } catch (uniffi.arithmetic.ArithmeticException.IntegerOverflow e) {
            // Expected
        }

        System.out.println("omit_checksums test passed - library works without checksum verification!");
    }
}
