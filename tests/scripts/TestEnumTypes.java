/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.enum_types.*;

public class TestEnumTypes {
    public static void main(String[] args) {
        testFlatEnums();
        testDiscriminants();
        testFieldedEnums();
        testBoxedVariant();

        System.out.println("All enum type tests passed!");
    }

    static void testFlatEnums() {
        assert EnumTypes.getAnimal(Animal.CAT) == Animal.CAT : "flat enum roundtrip failed";
        assert EnumTypes.getAnimal(null) == Animal.DOG : "absent optional should fall back to Dog";
    }

    static void testDiscriminants() {
        // A repr wider than int has to carry an `L` suffix through to the Java literal.
        assert AnimalLargeUInt.values().length == 2 : "expected two large-uint variants";
        assert AnimalSignedInt.values().length == 5 : "expected five signed variants";
        assert AnimalUInt.valueOf("DOG") == AnimalUInt.DOG : "unsigned repr enum should name DOG";
        assert AnimalNoReprInt.values().length == 2 : "expected two no-repr variants";
    }

    static void testFieldedEnums() {
        // Tuple variants get positional component names, which the generated close() has to use.
        AnimalEnum cat = EnumTypes.getAnimalEnum(Animal.CAT);
        assert cat instanceof AnimalEnum.Cat : "expected the Cat variant";
        assert ((AnimalEnum.Cat) cat).v1().name().equals("cat") : "unexpected Cat payload";

        // Closing through the interface, which has to redeclare close() without `throws Exception`
        // for this to compile.
        try (AnimalEnum dog = EnumTypes.getAnimalEnum(Animal.DOG)) {
            assert dog instanceof AnimalEnum.Dog : "expected the Dog variant";
            assert ((AnimalEnum.Dog) dog).v1().getRecord().name().equals("dog")
                : "unexpected Dog payload";
        }
    }

    static void testBoxedVariant() {
        EnumWithBoxedVariant boxed = EnumTypes.createBoxedEnum("hello");
        assert boxed instanceof EnumWithBoxedVariant.Boxed : "expected the Boxed variant";

        // Box<T> exists only in the scaffolding, so the variant field is typed as plain T.
        BoxedContent content = ((EnumWithBoxedVariant.Boxed) boxed).v1();
        assert content.value().equals("hello") : "unexpected boxed payload";

        assert EnumTypes.getBoxedEnumValue(boxed).equals("hello") : "boxed enum value roundtrip failed";
        assert EnumTypes.getBoxedEnumValue(new EnumWithBoxedVariant.Empty()).equals("empty")
            : "empty variant should report empty";

        assert EnumTypes.roundtripBoxedRecord(new BoxedContent("direct")).value().equals("direct")
            : "Box<Record> roundtrip failed";
    }
}
