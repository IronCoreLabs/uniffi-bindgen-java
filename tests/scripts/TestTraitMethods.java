import uniffi.trait_methods.*;
import java.util.HashMap;

public class TestTraitMethods {
    public static void main(String[] args) throws Exception {
        // Test TraitMethods object (UDL-defined with Display, Debug, Eq, Hash, Ord)
        TraitMethods m = new TraitMethods("yo");
        assert m.toString().equals("TraitMethods(yo)") : "toString failed: " + m.toString();

        assert m.equals(new TraitMethods("yo")) : "equals with same value failed";
        assert !m.equals(new TraitMethods("yoyo")) : "equals with different value should fail";

        // Test hashCode via HashMap
        HashMap<TraitMethods, Integer> map = new HashMap<>();
        map.put(m, 1);
        map.put(new TraitMethods("yoyo"), 2);
        assert map.get(m) == 1 : "HashMap lookup failed for m";
        assert map.get(new TraitMethods("yoyo")) == 2 : "HashMap lookup failed for yoyo";

        // Test compareTo (Ord trait)
        assert new ProcTraitMethods("a").compareTo(new ProcTraitMethods("b")) < 0 : "a < b failed";
        assert m.compareTo(new TraitMethods("z")) < 0 : "yo < z failed";
        assert m.compareTo(new TraitMethods("z")) <= 0 : "yo <= z failed";
        assert new TraitMethods("z").compareTo(m) > 0 : "z > yo failed";

        // Test Records - UdlRecord
        assert new UdlRecord("yo", (byte) 2).equals(new UdlRecord("yo", (byte) 2)) : "UdlRecord equals same failed";
        // Note: UdlRecord only compares the string field, not the int
        assert new UdlRecord("yo", (byte) 2).equals(new UdlRecord("yo", (byte) 3)) : "UdlRecord equals different i failed";
        assert !new UdlRecord("hi", (byte) 2).equals(new UdlRecord("yo", (byte) 3)) : "UdlRecord not equals different s failed";
        assert new UdlRecord("a", (byte) 2).compareTo(new UdlRecord("yo", (byte) 1)) < 0 : "UdlRecord a < yo failed";

        // Test Records - TraitRecord
        assert new TraitRecord("yo", (byte) 2).toString().equals("TraitRecord { s: \"yo\", i: 2 }") : "TraitRecord toString failed";
        assert new TraitRecord("yo", (byte) 2).equals(new TraitRecord("yo", (byte) 2)) : "TraitRecord equals same failed";
        // Note: TraitRecord only compares the string field, not the int
        assert new TraitRecord("yo", (byte) 2).equals(new TraitRecord("yo", (byte) 3)) : "TraitRecord equals different i failed";
        assert !new TraitRecord("hi", (byte) 2).equals(new TraitRecord("yo", (byte) 3)) : "TraitRecord not equals different s failed";
        assert new TraitRecord("a", (byte) 2).compareTo(new TraitRecord("yo", (byte) 1)) < 0 : "TraitRecord a < yo failed";

        // Test Enums - TraitEnum (uses Display)
        assert new TraitEnum.S("hello").toString().equals("TraitEnum::S(\"hello\")") : "TraitEnum.S toString failed: " + new TraitEnum.S("hello").toString();
        assert new TraitEnum.I((byte) 1).toString().equals("TraitEnum::I(1)") : "TraitEnum.I toString failed: " + new TraitEnum.I((byte) 1).toString();

        // Test Enums - UdlEnum (uses Debug for toString)
        assert new UdlEnum.S("hello").toString().equals("S { s: \"hello\" }") : "UdlEnum.S toString failed: " + new UdlEnum.S("hello").toString();

        // Test enum equals (only compares variant, not content)
        assert new UdlEnum.S("hello").equals(new UdlEnum.S("hello")) : "UdlEnum.S equals same failed";
        assert new UdlEnum.S("hello").equals(new UdlEnum.S("other")) : "UdlEnum.S equals different content failed";
        assert new UdlEnum.S("hello").compareTo(new UdlEnum.I((byte) 0)) < 0 : "UdlEnum S < I failed";

        assert new TraitEnum.I((byte) 1).equals(new TraitEnum.I((byte) 1)) : "TraitEnum.I equals same failed";
        assert new TraitEnum.I((byte) 1).equals(new TraitEnum.I((byte) 2)) : "TraitEnum.I equals different content failed";
        assert new TraitEnum.S("hello").compareTo(new TraitEnum.I((byte) 0)) < 0 : "TraitEnum S < I failed";

    }
}
