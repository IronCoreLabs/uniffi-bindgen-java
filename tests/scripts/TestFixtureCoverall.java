/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.coverall.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;

public class TestFixtureCoverall {
  public static void main(String[] args) throws Exception {
    // Test some_dict()
    try (var d = Coverall.createSomeDict()) {
      assert d.text().equals("text");
      assert d.maybeText().equals("maybe_text");
      assert Arrays.equals(d.someBytes(), "some_bytes".getBytes(StandardCharsets.UTF_8));
      assert Arrays.equals(d.maybeSomeBytes(), "maybe_some_bytes".getBytes(StandardCharsets.UTF_8));
      assert d.aBool();
      assert d.maybeABool() == false;
      assert d.unsigned8() == (byte)1;
      assert d.maybeUnsigned8() == (byte)2;
      assert d.unsigned16() == (short)3;
      assert d.maybeUnsigned16() == (short)4;
      assert d.unsigned64() == Long.parseUnsignedLong("18446744073709551615");
      assert d.maybeUnsigned64() == 0L;
      assert d.signed8() == (byte)8;
      assert d.maybeSigned8() == (byte)0;
      assert d.signed64() == 9223372036854775807L;
      assert d.maybeSigned64() == 0L;

      // floats should be "close enough".
      assert almostEquals(d.float32(), 1.2345F);
      assert almostEquals(d.maybeFloat32(), 22.0F/7.0F);
      assert almostEquals(d.float64(), 0.0);
      assert almostEquals(d.maybeFloat64(), 1.0);

      assert d.coveralls().getName().equals("some_dict");
      assert d.coverallsList().get(0).getName().equals("some_dict_1");
      assert d.coverallsList().get(1) == null;
      assert d.coverallsList().get(2).getName().equals("some_dict_2");

      assert d.coverallsMap().get("some_dict_3").getName().equals("some_dict_3");
      assert d.coverallsMap().get("none") == null;
      assert d.coverallsMap().get("some_dict_4").getName().equals("some_dict_4");

      assert Coverall.getNumAlive() == Long.parseUnsignedLong("5");
    }
    
    assert Coverall.getNumAlive() == Long.parseUnsignedLong("0");

    try (var d = Coverall.createNoneDict()) {
      assert d.text().equals("text");
      assert d.maybeText() == null;
      assert Arrays.equals(d.someBytes(), "some_bytes".getBytes(StandardCharsets.UTF_8));
      assert d.maybeSomeBytes() == null;
      assert d.aBool();
      assert d.maybeABool() == null;
      assert d.unsigned8() == (byte) 1;
      assert d.maybeUnsigned8() == null;
      assert d.unsigned16() == (short) 3;
      assert d.maybeUnsigned16() == null;
      assert d.unsigned64() == Long.parseUnsignedLong("18446744073709551615");
      assert d.maybeUnsigned64() == null;
      assert d.signed8() == (byte) 8;
      assert d.maybeSigned8() == null;
      assert d.signed64() == 9223372036854775807L;
      assert d.maybeSigned64() == null;

      // floats should be "close enough".
      assert almostEquals(d.float32(), 1.2345F);
      assert d.maybeFloat32() == null;
      assert almostEquals(d.float64(), 0.0);
      assert d.maybeFloat64() == null;

      assert d.coveralls() == null;
      
      assert Coverall.getNumAlive() == Long.parseUnsignedLong("0");
    }

    assert Coverall.getNumAlive() == Long.parseUnsignedLong("0");
    
    // Test arcs.
    try (var coveralls = new Coveralls("test_arcs")) {
      assert Coverall.getNumAlive() == 1;
      // One ref held by the foreign-language code, one created for this method call.
      assert coveralls.strongCount() == 2;
      assert coveralls.getOther() == null;
      coveralls.takeOther(coveralls);
      // Should now be a new strong ref, held by the object's reference to itself.
      assert coveralls.strongCount() == 3;
      // But the same number of instances.
      assert Coverall.getNumAlive() == 1;
      // Careful, this makes a new Java object which must be separately destroyed.
      try (Coveralls other = coveralls.getOther()) {
        // It's the same Rust object.
        assert other.getName().equals("test_arcs");
      }
      try {
        coveralls.takeOtherFallible();
        throw new RuntimeException("Should have thrown an IntegerOverflow exception!");
      } catch (CoverallException.TooManyHoles e) {
        // It's okay!
      }
      try {
        coveralls.takeOtherPanic("expected panic: with an arc!");
        throw new RuntimeException("Should have thrown an InternalException!");
      } catch (InternalException e) {
        // No problemo!
      }

      try {
        coveralls.falliblePanic("Expected panic in a fallible function!");
        throw new RuntimeException("Should have thrown an InternalException");
      } catch (InternalException e) {
        // No problemo!
      }
      coveralls.takeOther(null);
      assert coveralls.strongCount() == 2;
    }
    assert Coverall.getNumAlive() == 0;
    
    // Test return objects.
    try (var coveralls = new Coveralls("test_return_objects")) {
      assert Coverall.getNumAlive() == 1;
      assert coveralls.strongCount() == 2;
      try (Coveralls c2 = coveralls.cloneMe()) {
        assert c2.getName().equals(coveralls.getName());
        assert Coverall.getNumAlive() == 2;
        assert c2.strongCount() == 2;

        coveralls.takeOther(c2);
        // same number alive but `c2` has an additional ref count.
        assert Coverall.getNumAlive() == 2;
        assert coveralls.strongCount() == 2;
        assert c2.strongCount() == 3;
      }
      // Here we've dropped Java's reference to `c2`, but the rust struct will not
      // be dropped as coveralls hold an `Arc<>` to it.
      assert Coverall.getNumAlive() == 2;
    }
    // Destroying `coveralls` will kill both.
    assert Coverall.getNumAlive() == 0;
    
    // Test simple errors.
    try (var coveralls = new Coveralls("test_simple_errors")) {
      try {
        coveralls.maybeThrow(true);
        throw new RuntimeException("Expected method to throw exception");
      } catch (CoverallException.TooManyHoles e) {
        // Expected result
        assert e.getMessage().equals("The coverall has too many holes");
      }

      try {
        coveralls.maybeThrowInto(true);
        throw new RuntimeException("Expected method to throw exception");
      } catch (CoverallException.TooManyHoles e) {
        // Expected result
      }

      try {
        coveralls.panic("oops");
        throw new RuntimeException("Expected method to throw exception");
      } catch (InternalException e) {
        // Expected result
        assert e.getMessage().equals("oops");
      }
    }
    
    // Test complex errors.
    try (var coveralls = new Coveralls("test_complex_errors")) {
      assert coveralls.maybeThrowComplex((byte)0);

      try {
        coveralls.maybeThrowComplex((byte)1);
        throw new RuntimeException("Expected method to throw exception");
      } catch (ComplexException.OsException e) {
        assert e.code() == 10;
        assert e.extendedCode() == 20;
        assert e.toString().equals("uniffi.coverall.ComplexException$OsException: code=10, extendedCode=20");
      }

      try {
        coveralls.maybeThrowComplex((byte)2);
        throw new RuntimeException("Expected method to throw exception");
      } catch (ComplexException.PermissionDenied e) {
        assert e.reason().equals("Forbidden");
        assert e.toString().equals("uniffi.coverall.ComplexException$PermissionDenied: reason=Forbidden");
      }

      try {
        coveralls.maybeThrowComplex((byte)3);
        throw new RuntimeException("Expected method to throw exception");
      } catch (ComplexException.UnknownException e) {
        assert e.toString().equals("uniffi.coverall.ComplexException$UnknownException: ");
      }

      try {
        coveralls.maybeThrowComplex((byte)4);
        throw new RuntimeException("Expected method to throw exception");
      } catch (InternalException e) {
        // Expected result
      }
    }
    
    // Test error values.
    try (var coveralls = new Coveralls("test_error_values")) {
      try {
        Coverall.throwRootError();
        throw new RuntimeException("Expected method to throw exception");
      } catch (RootException.Complex e) {
        assert e.error() instanceof ComplexException.OsException;
      }

      RootException e = Coverall.getRootError();
      if (e instanceof RootException.Other) {
        assert ((RootException.Other) e).error() == OtherError.UNEXPECTED;
      } else {
        throw new RuntimeException("Unexpected error subclass");
      }

      ComplexException ce = Coverall.getComplexError(null);
      assert ce instanceof ComplexException.PermissionDenied;

      assert Coverall.getErrorDict(null).complexError() == null;
    }
    
    // Test interfaces in dicts.
    try (Coveralls coveralls = new Coveralls("test_interfaces_in_dicts")) {
      coveralls.addPatch(new Patch(Color.RED));
      coveralls.addRepair(new Repair(Instant.now(), new Patch(Color.BLUE)));
      assert coveralls.getRepairs().size() == 2;
    }
    
    // Test regressions.
    try (Coveralls coveralls = new Coveralls("test_regressions")) {
      assert coveralls.getStatus("success").equals("status: success");
    }
    
    // Test empty records.
    try (Coveralls coveralls = new Coveralls("test_empty_records")) {
      assert coveralls.setAndGetEmptyStruct(new EmptyStruct()).equals(new EmptyStruct());
      assert new EmptyStruct() != new EmptyStruct();
    }
    
    // The GC test; we should have 1000 alive by the end of the loop.
    //
    // Later on, nearer the end of the script, we'll test again, when the cleaner
    // has had time to clean up.
    //
    // The number alive then should be zero.
    // First we make 1000 objects, and wait for the rest of the test to run. If it has, then
    // the garbage objects have been collected, and the Rust counter parts have been dropped.
    for (int i = 0; i < 1000; i++) {
      Coveralls c = new Coveralls("GC testing " + i);
    }
    
    // Java Getter trait implementation for tests.
    class JavaGetters implements Getters {
      @Override
      public Boolean getBool(Boolean v, Boolean arg2) {
        return v != arg2;
      }
      
      @Override
      public String getString(String v, Boolean arg2) throws CoverallException {
        if (v.equals("too-many-holes")) {
          throw new CoverallException.TooManyHoles("too many holes");
        } else if (v.equals("unexpected-error")) {
          throw new RuntimeException("unexpected error");
        } else if (arg2) {
          return v.toUpperCase();
        } else {
          return v;
        }
      }
      
      @Override
      public String getOption(String v, Boolean arg2) throws ComplexException {
        if (v.equals("os-error")) {
          throw new ComplexException.OsException((short)100, (short)200);
        } else if (v.equals("unknown-error")) {
          throw new ComplexException.UnknownException();
        } else if (arg2) {
          if (!v.isEmpty()) {
            return v.toUpperCase();
          } else {
            return null;
          }
        } else {
          return v;
        }
      }
      
      @Override
      public List<Integer> getList(List<Integer> v, Boolean arg2) {
        if (arg2) {
          return v;
        } else {
          return List.of();
        }
      }
      
      @Override
      public void getNothing(String v) {}
      
      @Override
      public Coveralls roundTripObject(Coveralls coveralls) {
        return coveralls;
      }
    }
          
    // Test traits implemented in Rust.
    {
      var rustGetters = Coverall.makeRustGetters();
      Coverall.testGetters(rustGetters);
      testGettersFromJava(rustGetters);
    }

    // Test traits implemented in Java.
    {
      var javaGetters = new JavaGetters();
      Coverall.testGetters(javaGetters);
      testGettersFromJava(javaGetters);
    }

    class JavaNode implements NodeTrait {
      NodeTrait currentParent;
      
      @Override
      public String name() {
        return "node-kt";
      }
      
      @Override
      public void setParent(NodeTrait parent) {
        this.currentParent = parent;
      }

      @Override
      public NodeTrait getParent() {
        return currentParent;
      }

      @Override
      public Long strongCount() {
        return 0L;
      }
    }

    // Test NodeTrait
    {
      List<NodeTraitImpl> traits = (List<NodeTraitImpl>)(List<?>)Coverall.getTraits();
      assert traits.get(0).name().equals("node-1");
      // Note: strong counts are 1 more than you might expect, because the strongCount() method
      // holds a strong ref.
      assert traits.get(0).strongCount() == 2L;

      assert traits.get(1).name().equals("node-2");
      assert traits.get(1).strongCount() == 2L;

      // Note: this doesn't increase the Rust strong count, since we wrap the Rust impl with a
      // Swift impl before passing it to `setParent()`
      traits.get(0).setParent(traits.get(1));
      assert Coverall.ancestorNames(traits.get(0)).equals(Arrays.asList("node-2"));
      assert Coverall.ancestorNames(traits.get(1)).isEmpty();
      assert traits.get(1).strongCount() == 2L;
      assert traits.get(0).getParent().name().equals("node-2");

      JavaNode javaNode = new JavaNode();
      traits.get(1).setParent(javaNode);
      assert Coverall.ancestorNames(traits.get(0)).equals(Arrays.asList("node-2", "node-kt"));
      assert Coverall.ancestorNames(traits.get(1)).equals(Arrays.asList("node-kt"));
      assert Coverall.ancestorNames(javaNode).isEmpty();

      traits.get(1).setParent(null);
      javaNode.setParent(traits.get(0));
      assert Coverall.ancestorNames(javaNode).equals(Arrays.asList("node-1", "node-2"));
      assert Coverall.ancestorNames(traits.get(0)).equals(Arrays.asList("node-2"));
      assert Coverall.ancestorNames(traits.get(1)).isEmpty();

      // Unset everything and check that we don't get a memory error
      javaNode.setParent(null);
      traits.get(0).setParent(null);
      for (NodeTraitImpl node : traits) {
        node.close();
      }
    }

    {
      var rustGetters = Coverall.makeRustGetters();
      // Check that these don't cause use-after-free bugs
      Coverall.testRoundTripThroughRust(rustGetters);
      Coverall.testRoundTripThroughForeign(new JavaGetters());
    }

    // Test StringUtil
    {
      var traits = Coverall.getStringUtilTraits();
      assert traits.get(0).concat("cow", "boy").equals("cowboy");
      assert traits.get(1).concat("cow", "boy").equals("cowboy");
    }

    // This tests that the UniFFI-generated scaffolding doesn't introduce any unexpected locking.
    // We have one thread busy-wait for a some period of time, while a second thread repeatedly
    // increments the counter and then checks if the object is still busy. The second thread should
    // not be blocked on the first, and should reliably observe the first thread being busy.
    // If it does not, that suggests UniFFI is accidentally serializing the two threads on access
    // to the shared counter object.
    try (ThreadsafeCounter counter = new ThreadsafeCounter()) {
      ExecutorService executor = Executors.newFixedThreadPool(3);
      try {
        var busyWaiting = executor.submit(() -> {
          // 300 ms should be long enough for the other thread to easily finish
          // its loop, but not so long as to annoy the user with a slow test.
          counter.busyWait(300);
        });
        Future<Integer> incrementing = executor.submit(() -> {
          Integer count = 0;
          for (int n = 0; n < 100; n++) {
            // We expect most iterations of this loop to run concurrently with the busy-waiting thread.
            count = counter.incrementIfBusy();
          }
          return count;
        });

        busyWaiting.get();
        Integer count = incrementing.get();
        assert count > 0 : MessageFormat.format("Counter doing the locking: incrementIfBusy={0}", count);
      } finally {
        executor.shutdown();
      }
    }

    // This does not call Rust code.
    // no defaults in Java, this code doesn't pass
    // DictWithDefaults d = new DictWithDefaults();
    // assert d.name().equals("default-value");
    // assert d.category() == null;
    // assert d.integer() == 31L;

    DictWithDefaults d = new DictWithDefaults("this", "that", 42L);
    assert d.name().equals("this");
    assert d.category().equals("that");
    assert d.integer() == 42L;

    // Test bytes
    try (Coveralls coveralls = new Coveralls("test_bytes")) {
      assert new String(coveralls.reverse("123".getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).equals("321");
    }

    // Test fakes using open classes
    class FakePatch extends Patch {
      private Color color;
  
      public FakePatch(Color color) {
        super(NoPointer.INSTANCE);
        this.color = color;
      }

      @Override
      public Color getColor() {
        return this.color;
      }
    } 

    class FakeCoveralls extends Coveralls {
      private String name;
      private ArrayList<Repair> repairs = new ArrayList(List.of());

      public FakeCoveralls(String name) {
        super(NoPointer.INSTANCE);
        this.name = name;
      }

      @Override
      public void addPatch(Patch patch) {
        repairs.add(new Repair(Instant.now(), patch));
      }

      @Override
      public List<Repair> getRepairs() {
        return repairs;
      }
    }

    try (FakeCoveralls coveralls = new FakeCoveralls("using_fakes")) {
      Patch patch = new FakePatch(Color.RED);
      coveralls.addPatch(patch);
      assert !coveralls.getRepairs().isEmpty();
    }

    try (FakeCoveralls coveralls = new FakeCoveralls("using_fakes_and_calling_methods_without_override_crashes")) {
      Throwable exception = null;
      try {
        coveralls.cloneMe();
      } catch (Throwable e) {
        exception = e;
      }
      assert exception != null;
    }

    try (FakeCoveralls coveralls = new FakeCoveralls("using_fallible_constructors")) {
      Throwable exception = null;
      try {
        new FalliblePatch();
      } catch (Throwable e) {
        exception = e;
      }
      assert exception != null;

      exception = null;

      try {
        FalliblePatch.secondary();
      } catch (Throwable e) {
        exception = e;
      }
      assert exception != null;
    }

    try (Coveralls coveralls = new Coveralls("using_fakes_with_real_objects_crashes")) {
      FakePatch patch = new FakePatch(Color.RED);
      Throwable exception = null;
      try {
        coveralls.addPatch(patch);
      } catch (Throwable e) {
        exception = e;
      }
      assert exception != null;
    }

    // This is from an earlier GC test; ealier, we made 1000 new objects.
    // By now, the GC has had time to clean up, and now we should see 0 alive.
    // (hah! Wishful-thinking there ;)
    // * We need to System.gc() and/or sleep.
    // * There's one stray thing alive, not sure what that is, but it's unrelated.
    for (int i = 0; i < 100; i++) {
      if (Coverall.getNumAlive() <= 1L) {
        break;
      }
      System.gc();
      Thread.sleep(100);
    }

    assert Coverall.getNumAlive() <= 1L : MessageFormat.format("Num alive is {0}. GC/Cleaner thread has starved", Coverall.getNumAlive());
  }
  
  public static boolean almostEquals(float a, float b) {
    return Math.abs(a - b) < .000001f;
  }
  
  public static boolean almostEquals(double a, double b) {
    return Math.abs(a - b) < .000001d;
  }
  
  // Test traits from Java.
  static void testGettersFromJava(Getters getters) {
    assert getters.getBool(true, true) == false;
    assert getters.getBool(true, false) == true;
    assert getters.getBool(false, true) == true;
    assert getters.getBool(false, false) == false;

    try {
      assert getters.getString("hello", false).equals("hello");
      assert getters.getString("hello", true).equals("HELLO");
    } catch (CoverallException e) {
      throw new RuntimeException("Unexpected CoverallException", e);
    }

    try {
      assert getters.getOption("hello", true).equals("HELLO");
      assert getters.getOption("hello", false).equals("hello");
      assert getters.getOption("", true) == null;
    } catch (ComplexException e) {
      throw new RuntimeException("Unexpected ComplexException", e);
    }

    assert getters.getList(Arrays.asList(1, 2, 3), true).equals(Arrays.asList(1, 2, 3));
    assert getters.getList(Arrays.asList(1, 2, 3), false).equals(Arrays.asList());

    // void function returns nothing, compiler won't let us write this test
    // assert getters.getNothing("hello") == Unit;

    try {
      getters.getString("too-many-holes", true);
      throw new RuntimeException("Expected method to throw exception");
    } catch (CoverallException.TooManyHoles e) {
      // Expected
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      getters.getOption("os-error", true);
      throw new RuntimeException("Expected method to throw exception");
    } catch (ComplexException.OsException e) {
      assert e.code().intValue() == 100;
      assert e.extendedCode().intValue() == 200;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      getters.getOption("unknown-error", true);
      throw new RuntimeException("Expected method to throw exception");
    } catch (ComplexException.UnknownException e) {
      // Expected
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    try {
      getters.getString("unexpected-error", true);
    } catch (Exception e) {
      // Expected
    } 
  }
}
