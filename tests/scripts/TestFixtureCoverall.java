/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.coverall.*;

import java.time.Instant;
import java.util.concurrent.*;

public class TestFixtureCoverall {
  public static void main(String[] args) throws Exception {
    // Test some_dict()
    {
      var d = Coverall.createSomeDict();
      assert d.text().equals("text");
    }
  }
}
