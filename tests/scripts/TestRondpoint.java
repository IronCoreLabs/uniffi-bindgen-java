import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import uniffi.rondpoint.*;

public class TestRondpoint {
  public static void main(String[] args) throws Exception {
    Dictionnaire dico = new Dictionnaire(Enumeration.DEUX, true, (byte)0, 123456789L);
    Dictionnaire copyDico = Rondpoint.copieDictionnaire(dico);
    assert dico.equals(copyDico);
    
    assert Rondpoint.copieEnumeration(Enumeration.DEUX).equals(Enumeration.DEUX);
    assert Rondpoint.copieEnumerations(List.of(Enumeration.UN, Enumeration.DEUX)).equals(List.of(Enumeration.UN, Enumeration.DEUX));
    assert Rondpoint.copieCarte(Map.ofEntries(
      Map.entry("0", new EnumerationAvecDonnees.Zero()),
      Map.entry("1", new EnumerationAvecDonnees.Un(1)),
      Map.entry("2", new EnumerationAvecDonnees.Deux(2, "deux"))
    )).equals(Map.<String, EnumerationAvecDonnees>ofEntries(
      Map.entry("0", new EnumerationAvecDonnees.Zero()),
      Map.entry("1", new EnumerationAvecDonnees.Un(1)),
      Map.entry("2", new EnumerationAvecDonnees.Deux(2, "deux")))
    );

    var var1 = new EnumerationAvecDonnees.Zero();
    var var2 = new EnumerationAvecDonnees.Un(1);
    var var3 = new EnumerationAvecDonnees.Un(2);
    assert !var1.equals(var2);
    assert !var2.equals(var3);
    assert var1.equals(new EnumerationAvecDonnees.Zero());
    assert !var1.equals(new EnumerationAvecDonnees.Un(1));
    assert var2.equals(new EnumerationAvecDonnees.Un(1));

    assert Rondpoint.switcheroo(false);

    // Test the roundtrip across the FFI.
    // This shows that the values we send come back in exactly the same state as we sent them.
    // i.e. it shows that lowering from Java and lifting into Rust is symmetrical with 
    //      lowering from Rust and lifting into Java.
    var rt = new Retourneur();

    // Booleans
    affirmAllerRetour(List.of(true, false), rt::identiqueBoolean);

    // Bytes
    affirmAllerRetour(List.of(Byte.MIN_VALUE, Byte.MAX_VALUE), rt::identiqueI8);
    affirmAllerRetour(List.of((byte)0x00, (byte)0xFF), rt::identiqueU8);

    // Shorts
    affirmAllerRetour(List.of(Short.MIN_VALUE, Short.MAX_VALUE), rt::identiqueI16);
    affirmAllerRetour(List.of((short)0x0000, (short)0xFFFF), rt::identiqueU16);

    // Ints
    affirmAllerRetour(List.of(0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE), rt::identiqueI32);
    affirmAllerRetour(List.of(0x00000000, 0xFFFFFFFF), rt::identiqueU32);

    // Longs
    affirmAllerRetour(List.of(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE), rt::identiqueI64);
    affirmAllerRetour(List.of(0L, 1L, 0L, Long.MAX_VALUE), rt::identiqueU64);

    // Floats
    affirmAllerRetour(List.of(0.0F, 0.5F, 0.25F, Float.MIN_VALUE, Float.MAX_VALUE), rt::identiqueFloat);

    // Doubles
    affirmAllerRetour(List.of(0.0, 1.0, Double.MIN_VALUE, Double.MAX_VALUE), rt::identiqueDouble);

    // Strings
    affirmAllerRetour(List.of("", "abc", "null\u0000byte", "été", "ښي لاس ته لوستلو لوستل", "😻emoji 👨‍👧‍👦multi-emoji, 🇨🇭a flag, a canal, panama"), rt::identiqueString);

    // Records of primitives
    affirmAllerRetour(List.of(-1, 0, 1).stream().map(i -> new DictionnaireNombresSignes(i.byteValue(), i.shortValue(), i, i.longValue())).collect(Collectors.toList()), rt::identiqueNombresSignes);
    affirmAllerRetour(List.of(0, 1).stream().map(i -> new DictionnaireNombres(i.byteValue(), i.shortValue(), i, i.longValue())).collect(Collectors.toList()), rt::identiqueNombres);

    rt.close();

    // Test one way across the FFI.
    //
    // We send one representation of a value to lib.rs, and it transforms it into another, a string.
    // lib.rs sends the string back, and then we compare here in Java.
    //
    // This shows that the values are transformed into strings the same way in both Java and Rust.
    // i.e. if we assume that the string return works (we test this assumption elsewhere)
    //      we show that lowering from kotlin and lifting into rust has values that both Java and Rust
    //      both stringify in the same way. i.e. the same values.
    //
    // If we roundtripping proves the symmetry of our lowering/lifting from here to Rust, and lowering/lifting from Rust to here,
    // and this convinces us that lowering/lifting from here to Rust is correct, then 
    // together, we've shown the correctness of the return leg.
    var st = new Stringifier();

    // Test the efficacy of the string transport from Rust. If this fails, but everything else 
    // works, then things are very weird.
    var wellKnown = st.wellKnownString("java");
    assert "uniffi 💚 java!".equals(wellKnown) : MessageFormat.format("wellKnownString 'uniffi 💚 java!' == '{0}'", wellKnown);

    // Booleans
    affirmEnchaine(List.of(true, false), st::toStringBoolean, TestRondpoint::defaultStringyEquals);
  
    // All primitives are signed in Java by default. Rust correctly interprets the same signed max as an unsigned max
    // when told to. We have to mask the value we expect on the comparison side for Java, or else it will toString them
    // as signed values. Callers of Uniffi functions need to be aware when making comparisons (`compareUnsigned`) or
    // printing when a value is actually unsigned to code around footguns on this side.
    // Bytes
    affirmEnchaine(List.of(Byte.MIN_VALUE, Byte.MAX_VALUE), st::toStringI8, TestRondpoint::defaultStringyEquals);
    affirmEnchaine(List.of(Byte.MIN_VALUE, Byte.MAX_VALUE), st::toStringU8, (obs, exp) -> obs.equals(String.valueOf(exp & 0xff)));

    // Shorts
    affirmEnchaine(List.of(Short.MIN_VALUE, Short.MAX_VALUE), st::toStringI16, TestRondpoint::defaultStringyEquals);
    affirmEnchaine(List.of(Short.MIN_VALUE, Short.MAX_VALUE), st::toStringU16, (obs, exp) -> obs.equals(String.valueOf(exp & 0xffff)));

    // Ints
    affirmEnchaine(List.of(0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE), st::toStringI32, TestRondpoint::defaultStringyEquals);
    affirmEnchaine(List.of(0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE), st::toStringU32, (obs, exp) -> obs.equals(Integer.toUnsignedString(exp)));

    // Longs
    affirmEnchaine(List.of(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE), st::toStringI64, TestRondpoint::defaultStringyEquals);
    affirmEnchaine(List.of(0L, 1L, 0L, Long.MAX_VALUE), st::toStringU64, TestRondpoint::defaultStringyEquals);

    // Floats
    // MIN_VALUE is 1.4E-45. Accuracy and formatting get weird at small sizes.
    affirmEnchaine(List.of(0.0F, 1.0F, -1.0F, Float.MIN_VALUE, Float.MAX_VALUE), st::toStringFloat, (s, n) -> Float.parseFloat(s) == n);

    // Doubles
    // MIN_VALUE is 4.9E-324. Accuracy and formatting get weird at small sizes.
    affirmEnchaine(List.of(0.0, 1.0, -1.0, Double.MIN_VALUE, Double.MAX_VALUE), st::toStringDouble, (s, n) -> Double.parseDouble(s) == n);

    st.close();

    // Defaults aren't supported in Java, so we check that our Java `None` equivalent goes across the barrier correctly
    // as an option and comes back instead. See Kotlin's rondpoint tests for reference default behavior if you want to
    // PR defaults as a feature.
    // Step 1: call the methods without arguments, check that Option works.
    var op = new Optionneur();  
  
    // Optionals
    assert op.sinonNull(null) == null;
    assert op.sinonZero(null) == null;

        // Step 2. Convince ourselves that if we pass something else, then that changes the output.
    //         We have shown something coming out of the sinon methods, but without eyeballing the Rust
    //         we can't be sure that the arguments will change the return value.
    affirmAllerRetour(List.of("foo", "bar"), op::sinonString);
    affirmAllerRetour(List.of(true, false), op::sinonBoolean);
    affirmAllerRetour(List.of(List.of("a", "b"), List.of()), op::sinonSequence);

    // Optionals
    affirmAllerRetour(List.of("0", "1"), op::sinonNull);
    affirmAllerRetour(List.of(0, 1), op::sinonZero);

    // Integers
    affirmAllerRetour(List.of((byte)0, (byte)1), op::sinonI8Dec);
    affirmAllerRetour(List.of((byte)0, (byte)1), op::sinonU8Dec);
    affirmAllerRetour(List.of((short)0, (short)1), op::sinonI16Dec);
    affirmAllerRetour(List.of((short)0, (short)1), op::sinonU16Dec);
    affirmAllerRetour(List.of(0, 1), op::sinonI32Dec);
    affirmAllerRetour(List.of(0, 1), op::sinonU32Dec);
    affirmAllerRetour(List.of(0L, 1L), op::sinonI64Dec);
    affirmAllerRetour(List.of(0L, 1L), op::sinonU64Dec);

    // Hexadecimal integers
    affirmAllerRetour(List.of((byte)0, (byte)1), op::sinonI8Hex);
    affirmAllerRetour(List.of((byte)0, (byte)1), op::sinonU8Hex);
    affirmAllerRetour(List.of((short)0, (short)1), op::sinonI16Hex);
    affirmAllerRetour(List.of((short)0, (short)1), op::sinonU16Hex);
    affirmAllerRetour(List.of(0, 1), op::sinonI32Hex);
    affirmAllerRetour(List.of(0, 1), op::sinonU32Hex);
    affirmAllerRetour(List.of(0L, 1L), op::sinonI64Hex);
    affirmAllerRetour(List.of(0L, 1L), op::sinonU64Hex);

    // Octal integers
    affirmAllerRetour(List.of(0, 1), op::sinonU32Oct);

    // Floats
    affirmAllerRetour(List.of(0.0f, 1.0f), op::sinonF32);
    affirmAllerRetour(List.of(0.0, 1.0), op::sinonF64);

    // Enums
    affirmAllerRetour(List.of(Enumeration.values()), op::sinonEnum);

    op.close();
}

  private static <T> void affirmAllerRetour(List<T> vs, Function<T, T> f) {
    for (var v : vs) {
      assert v.equals(f.apply(v)) : MessageFormat.format("{0}({1})", f, v);
    }
  }

  private static <T> void affirmEnchaine(List<T> vs, Function<T, String> f, BiFunction<String, T, Boolean> equals) {
    for (var exp : vs) {
      var obs = f.apply(exp);
      assert equals.apply(obs, exp) : MessageFormat.format("{0}({1}): observed={2}, expected={3}", f, exp, obs, exp);
    }
  }

  private static <T> Boolean defaultStringyEquals(String obs, T exp) {
    return obs.equals(exp.toString());
  }
}
