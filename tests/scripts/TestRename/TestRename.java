/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.uniffi_fixture_rename.*;

public class TestRename {
    public static void main(String[] args) throws Exception {
        //
        // Test proc-macro based renaming (#[uniffi(name = "...")])
        // These apply to ALL languages
        //

        // Test renamed record
        RenamedRecord record = new RenamedRecord(42);
        assert record.item() == 42 : "RenamedRecord.item should be 42";

        // Test renamed enum with renamed variant (sealed interface)
        RenamedEnum enum1 = new RenamedEnum.RenamedVariant();
        RenamedEnum enum2 = new RenamedEnum.Record(record);
        assert enum2 instanceof RenamedEnum.Record : "enum2 should be RenamedEnum.Record";

        // Test renamed function (in namespace class)
        RenamedEnum result = UniffiFixtureRename.renamedFunction(record);
        assert result instanceof RenamedEnum.Record : "renamedFunction should return RenamedEnum.Record";
        assert ((RenamedEnum.Record) result).v1().item() == 42 : "Record item should be 42";

        // Test renamed object with renamed constructor and method
        RenamedObject obj = RenamedObject.renamedConstructor(123);
        assert obj != null : "RenamedObject should be created";
        int methodResult = obj.renamedMethod();
        assert methodResult == 123 : "renamedMethod should return 123";

        // Test trait method renaming (trait keeps original name)
        Trait traitImpl = UniffiFixtureRename.createTraitImpl(5);
        assert traitImpl.renamedTraitMethod(10) == 50 : "5 * 10 = 50";

        System.out.println("Proc-macro rename tests passed!");

        //
        // Test TOML-based renaming ([bindings.java.rename])
        // These are Java-specific renames
        //

        // Test renamed record with renamed field
        JavaRecord javaRecord = new JavaRecord(123);
        assert javaRecord.javaItem() == 123 : "JavaRecord.javaItem should be 123";

        // Test renamed function with renamed arg (in namespace class)
        JavaEnum javaResult = UniffiFixtureRename.javaFunction(javaRecord);
        assert javaResult instanceof JavaEnum.JavaRecordVariant : "javaFunction should return JavaEnum.JavaRecordVariant";

        // Test renamed enum with renamed variants (sealed interface)
        JavaEnum javaEnum1 = new JavaEnum.JavaVariantA();
        JavaEnum javaEnum2 = new JavaEnum.JavaRecordVariant(javaRecord);
        assert javaEnum1 instanceof JavaEnum.JavaVariantA : "javaEnum1 should be JavaVariantA";

        // Test renamed enum with fields
        JavaEnumWithFields withFields = new JavaEnumWithFields.JavaVariantA(1);
        assert withFields instanceof JavaEnumWithFields.JavaVariantA : "withFields should be JavaVariantA";

        // Test renamed error (throws renamed exception)
        try {
            UniffiFixtureRename.javaFunction(null);
            assert false : "Should have thrown JavaException";
        } catch (JavaException.JavaSimple e) {
            // Expected
        }

        // Test renamed object with renamed method and arg
        JavaObject javaObj = new JavaObject(100);
        try {
            int javaMethodResult = javaObj.javaMethod(10);
            assert javaMethodResult == 110 : "100 + 10 = 110";
        } catch (JavaException e) {
            assert false : "javaMethod should not throw";
        }

        // Test renamed trait
        JavaTrait javaTraitImpl = UniffiFixtureRename.createBindingTraitImpl(3);
        assert javaTraitImpl.javaTraitMethod(4) == 12 : "3 * 4 = 12";

        System.out.println("TOML-based rename tests passed!");
        System.out.println("TestRename: All tests passed!");
    }
}
