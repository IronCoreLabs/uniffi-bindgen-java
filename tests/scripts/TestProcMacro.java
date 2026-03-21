/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.proc_macro.*;

public class TestProcMacro {
    public static void main(String[] args) {
        // Test record methods
        One one = new One(42);
        assert one.getInnerValue() == 42 : "Record method getInnerValue() failed";

        // Test flat enum methods
        MaybeBool mb = MaybeBool.TRUE;
        assert mb.next() == MaybeBool.FALSE : "Flat enum method next() TRUE -> FALSE failed";
        assert MaybeBool.FALSE.next() == MaybeBool.UNCERTAIN : "Flat enum method next() FALSE -> UNCERTAIN failed";
        assert MaybeBool.UNCERTAIN.next() == MaybeBool.TRUE : "Flat enum method next() UNCERTAIN -> TRUE failed";

        // Test complex enum (sealed interface) methods
        MixedEnum meString = new MixedEnum.String("hello");
        assert meString.isNotNone() == true : "Complex enum method isNotNone() for String variant failed";

        MixedEnum meInt = new MixedEnum.Int(123L);
        assert meInt.isNotNone() == true : "Complex enum method isNotNone() for Int variant failed";

        MixedEnum meNone = new MixedEnum.None();
        assert meNone.isNotNone() == false : "Complex enum method isNotNone() for None variant failed";

        System.out.println("TestProcMacro: All tests passed!");
    }
}
