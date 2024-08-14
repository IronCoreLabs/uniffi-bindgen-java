/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.imported_types_lib.*;
import uniffi.imported_types_sublib.*;
import uniffi.uniffi_one_ns.*;
import uniffi.ext_types_custom.*;
import uniffi.custom_types.*;
import java.util.List;
import java.util.stream.Stream;

public class TestImportedTypes {
  public static void main(String[] args) throws Exception {
    var ct = ImportedTypesLib.getCombinedType(null);
    assert ct.uot().sval().equals("hello");
    assert ct.guid().equals(new Guid("a-guid"));
    assert ct.url().equals(new Url(new java.net.URL("http://example.com/")));

    var ct2 = ImportedTypesLib.getCombinedType(null);
    assert ct.equals(ct2);

    assert ImportedTypesLib.getObjectsType(null).maybeInterface() == null;
    assert ImportedTypesLib.getObjectsType(null).maybeTrait() == null;
    assert ImportedTypesLib.getUniffiOneTrait(null) == null;

    assert ImportedTypesSublib.getSubType(null).maybeInterface() == null;
    assert ImportedTypesSublib.getTraitImpl().hello().equals("sub-lib trait impl says hello");

    var url = new Url(new java.net.URL("http://example.com/"));
    assert ImportedTypesLib.getUrl(url).equals(url);
    assert ImportedTypesLib.getMaybeUrl(url).equals(url);
    assert ImportedTypesLib.getMaybeUrl(null) == null;
    assert ImportedTypesLib.getUrls(List.of(url)).equals(List.of(url));
    // List.of doesn't allow `null`, though `null` is allowed in `List` /shrug
    assert ImportedTypesLib.getMaybeUrls(Stream.of(url, null).toList()).equals(Stream.of(url, null).toList());

    assert ExtTypesCustom.getGuid(new Guid("guid")).equals(new Guid("guid"));
    assert ExtTypesCustom.getOuid(new Ouid("ouid")).equals(new Ouid("ouid"));
    // assert ImportedTypesLib.getImportedGuid(new Guid("guid")).equals(new Guid("guid"));
    assert ImportedTypesLib.getImportedOuid(new Ouid("ouid")).equals(new Ouid("ouid"));

    var uot = new UniffiOneType("hello");
    assert ImportedTypesLib.getUniffiOneType(uot).equals(uot);
    assert ImportedTypesLib.getMaybeUniffiOneType(uot).equals(uot);
    assert ImportedTypesLib.getMaybeUniffiOneType(null) == null;
    assert ImportedTypesLib.getUniffiOneTypes(List.of(uot)).equals(List.of(uot));
    assert ImportedTypesLib.getMaybeUniffiOneTypes(Stream.of(uot, null).toList()).equals(Stream.of(uot, null).toList());

    var uopmt = new UniffiOneProcMacroType("hello from proc-macro world");
    assert ImportedTypesLib.getUniffiOneProcMacroType(uopmt).equals(uopmt);
    assert UniffiOneNs.getMyProcMacroType(uopmt).equals(uopmt);

    var uoe = UniffiOneEnum.ONE;
    assert ImportedTypesLib.getUniffiOneEnum(uoe).equals(uoe);
    assert ImportedTypesLib.getMaybeUniffiOneEnum(uoe).equals(uoe);
    assert ImportedTypesLib.getMaybeUniffiOneEnum(null) == null;
    assert ImportedTypesLib.getUniffiOneEnums(List.of(uoe)).equals(List.of(uoe));
    assert ImportedTypesLib.getMaybeUniffiOneEnums(Stream.of(uoe, null).toList()).equals(Stream.of(uoe, null).toList());

    assert ct.ecd().sval().equals("ecd");
    assert ImportedTypesLib.getExternalCrateInterface("foo").value().equals("foo");
  }
}
