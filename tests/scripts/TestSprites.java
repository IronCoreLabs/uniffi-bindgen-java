/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.sprites.*;

public class TestSprites {
  public static void main(String[] args) throws Exception {
    // Test default position (null -> 0,0)
    try (var sempty = new Sprite((Point) null)) {
      assert sempty.getPosition().x() == 0.0;
      assert sempty.getPosition().y() == 0.0;
    }

    // Test initial position
    try (var s = new Sprite(new Point(0.0, 1.0))) {
      assert s.getPosition().x() == 0.0;
      assert s.getPosition().y() == 1.0;

      // Test moveTo
      s.moveTo(new Point(1.0, 2.0));
      assert s.getPosition().x() == 1.0;
      assert s.getPosition().y() == 2.0;

      // Test moveBy
      s.moveBy(new Vector(-4.0, 2.0));
      assert s.getPosition().x() == -3.0;
      assert s.getPosition().y() == 4.0;
    }

    // Test use-after-close
    {
      var s = new Sprite(new Point(0.0, 0.0));
      s.close();
      try {
        s.moveBy(new Vector(0.0, 0.0));
        throw new RuntimeException("Should not be able to call after close");
      } catch (IllegalStateException e) {
        // expected
      }
    }

    // Test alternative constructor
    try (var srel = Sprite.newRelativeTo(new Point(0.0, 1.0), new Vector(1.0, 1.5))) {
      assert srel.getPosition().x() == 1.0;
      assert srel.getPosition().y() == 2.5;
    }

    // Test namespace function
    Point translated = Sprites.translate(new Point(1.0, 2.0), new Vector(3.0, 4.0));
    assert translated.x() == 4.0;
    assert translated.y() == 6.0;
  }
}
