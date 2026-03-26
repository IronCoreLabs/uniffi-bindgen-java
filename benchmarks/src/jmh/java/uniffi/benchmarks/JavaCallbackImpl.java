/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package uniffi.benchmarks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaCallbackImpl implements TestCallbackInterface {

    private static final String LARGE_STRING_A = "a".repeat(2048);
    private static final String LARGE_STRING_B = "b".repeat(1500);

    @Override
    public void callOnly() {
    }

    @Override
    public double primitives(byte a, int b) {
        return (double) a + (double) b;
    }

    @Override
    public String strings(String a, String b) {
        return a + b;
    }

    @Override
    public String largeStrings(String a, String b) {
        return a + b;
    }

    @Override
    public TestRecord records(TestRecord a, TestRecord b) {
        return new TestRecord(a.a() + b.a(), a.b() + b.b(), a.c() + b.c());
    }

    @Override
    public TestEnum enums(TestEnum a, TestEnum b) {
        double aSum = switch (a) {
            case TestEnum.One one -> (double) one.a() + (double) one.b();
            case TestEnum.Two two -> two.c();
        };
        double bSum = switch (b) {
            case TestEnum.One one -> (double) one.a() + (double) one.b();
            case TestEnum.Two two -> two.c();
        };
        return new TestEnum.Two(aSum + bSum);
    }

    @Override
    public int[] vecs(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Override
    public Map<Integer, Integer> hashMaps(Map<Integer, Integer> a, Map<Integer, Integer> b) {
        var result = new HashMap<>(a);
        result.putAll(b);
        return result;
    }

    @Override
    public TestInterface interfaces(TestInterface a, TestInterface b) {
        return a.equals(b) ? a : b;
    }

    @Override
    public TestTraitInterface traitInterfaces(TestTraitInterface a, TestTraitInterface b) {
        return a.equals(b) ? a : b;
    }

    @Override
    public NestedData nestedData(NestedData a, NestedData b) {
        return a.equals(b) ? a : b;
    }

    @Override
    public int errors() throws TestException {
        throw new TestException.Two();
    }

    @Override
    public long runTest(TestCase testCase, long count) {
        long start = System.nanoTime();
        switch (testCase) {
            case CALL_ONLY -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseCallOnly();
                }
            }
            case PRIMITIVES -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCasePrimitives((byte) 0, 1);
                }
            }
            case STRINGS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseStrings("a", "b");
                }
            }
            case LARGE_STRINGS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseLargeStrings(LARGE_STRING_A, LARGE_STRING_B);
                }
            }
            case RECORDS -> {
                var rec1 = new TestRecord(-1, 1L, 1.5);
                var rec2 = new TestRecord(-2, 2L, 4.5);
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseRecords(rec1, rec2);
                }
            }
            case ENUMS -> {
                var e1 = new TestEnum.One(-1, 0L);
                var e2 = new TestEnum.Two(1.5);
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseEnums(e1, e2);
                }
            }
            case VECS -> {
                var v1 = new int[]{0, 1};
                var v2 = new int[]{2, 4, 6};
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseVecs(v1, v2);
                }
            }
            case HASHMAPS -> {
                var m1 = Map.of(0, 1, 1, 2);
                var m2 = Map.of(2, 4);
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseHashmaps(m1, m2);
                }
            }
            case INTERFACES -> {
                var iface1 = new TestInterface();
                var iface2 = new TestInterface();
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseInterfaces(iface1, iface2);
                }
                iface1.close();
                iface2.close();
            }
            case TRAIT_INTERFACES -> {
                var ti1 = Benchmarks.makeTestTraitInterface();
                var ti2 = Benchmarks.makeTestTraitInterface();
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseTraitInterfaces(ti1, ti2);
                }
            }
            case NESTED_DATA -> {
                var nd1 = new NestedData(
                    List.of(new TestRecord(-1, 1L, 1.5)),
                    List.of(List.of("one", "two"), List.of("three")),
                    Map.of("one", new TestEnum.One(-1, 1L), "two", new TestEnum.Two(0.5))
                );
                var nd2 = new NestedData(
                    List.of(new TestRecord(-2, 2L, 4.5)),
                    List.of(List.of("four", "five")),
                    Map.of("two", new TestEnum.Two(-0.5))
                );
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseNestedData(nd1, nd2);
                }
            }
            case ERRORS -> {
                for (long i = 0; i < count; i++) {
                    try {
                        Benchmarks.testCaseErrors();
                    } catch (TestException e) {
                        // expected
                    }
                }
            }
        }
        return System.nanoTime() - start;
    }
}
