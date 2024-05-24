import uniffi.arithmetic.*;

public class TestArithmetic {
  public static void main(String[] args) throws Exception {
    assert Arithmetic.add(2L, 4L) == 6L;   
    assert Arithmetic.add(4L, 8L) == 12L;

    try {
      Arithmetic.sub(0L, 2L);
      throw new RuntimeException("Should have thrown an IntegerOverflow exception!");
    } catch (uniffi.arithmetic.ArithmeticException.IntegerOverflow e) {
      // It's okay!
    }

    assert Arithmetic.sub(4L, 2L) == 2L;
    assert Arithmetic.sub(8L, 4L) == 4L;

    assert Arithmetic.div(8L, 4L) == 2L;

    try {
      Arithmetic.div(8L, 0L);
      throw new RuntimeException("Should have panicked when dividing by zero");
    } catch (RuntimeException e) {
      if (e instanceof InternalException) {
        // It's okay!
      } else {
        throw e;
      }
    }

    assert Arithmetic.equal(2L, 2L);
    assert Arithmetic.equal(4L, 4L);
    assert !Arithmetic.equal(2L, 4L);
    assert !Arithmetic.equal(4L, 8L);
  }
}
