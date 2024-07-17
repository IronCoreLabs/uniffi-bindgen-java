/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.imported_types_lib.*
import uniffi.imported_types_sublib.*
import uniffi.uniffi_one_ns.*
import uniffi.ext_types_custom.*

public class TestFutures {
  public static void main(String[] args) throws Exception {
    var ct = ???.getCombinedType(null);
    assert ct.uot().sval().equals("hello");
    asert ct.guid().equals("a-guid");
    assert ct.url().equals(new java.net.URL("http://example.com/"));

    var ct2 = ???.getCombinedType(null);
    assert ct.equals(ct2);

    assert ???.getObjectsType(null).maybeInterface() == null;
    assert ???.getObjectsType(null).maybeTrait() == null;
    assert ???.getUniffiOneTrait(null) == null;

    assert ???.getSubType(null).maybeInterface() == null;
    assert ???.getTraitImpl().hello().equals("sub-lib trait impl says hello");

    var url = new java.net.URL("http://example.com/");
    assert ???.getUrl(url).equals(url);
    assert ???.getMaybeUrl(url).equals(url);
    assert ???.getMaybeUrl(null) == null;
    assert ???.getUrls(List.of(url)).equals(List.of(url));
    assert ???.getMaybeUrls(List.of(url, null)).equals(List.of(url, null));

    assert ???.getGuid("guid").equals("guid");
    assert ???.getOuid("ouid").equals("ouid");
    //assert(getImportedGuid("guid") == "guid")
    assert ???.getImportedOuid("ouid").equals("ouid");

    var uot = new UniffiOneType("hello");
    assert ???.getUniffiOneType(uot).equals(uot);
    assert ???.getMaybeUniffiOneType(uot).equals(uot);
    assert ???.getMaybeUniffiOneType(null) == null;
    assert ???.getUniffiOneTypes(List.of(uot)).equals(List.of(uot));
    assert ???.getMaybeUniffiOneTypes(List.of(uot, null)).equals(List.of(uot, null));

    var uopmt = new UniffiOneProcMacroType("hello from proc-macro world");
    assert ???.getUniffiOneProcMacroType(uopmt).equals(uopmt);
    assert ???.getMyProcMacroType(uopmt).equals(uopmt);

    var uoe = UniffiOneEnum.ONE;
    assert ???.getUniffeOneEnum(uoe).equals(uoe);
    assert ???.getMaybeUniffiOneEnum(uoe).equals(uoe);
    assert ???.getMaybeUniffiOneEnum(null).equals(null);
    assert ???.getUniffiOneEnums(List.of(uoe)).equals(List.of(uoe));
    assert ???.getMaybeUniffiOneEnums(List.of(uoe, null)).equals(List.of(uoe, null));

    assert ct.ecd().sval().equals("ecd");
    assert ???.getExternalCrateInterface("foo").value().equals("foo");
  }
}
