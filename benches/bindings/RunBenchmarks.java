/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import uniffi.benchmarks.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class TestData {
    static final String testLargeString1 = "a".repeat(2048);
    static final String testLargeString2 = "b".repeat(1500);
    static final TestRecord testRec1 = new TestRecord(-1, 1L, 1.5);
    static final TestRecord testRec2 = new TestRecord(-2, 2L, 4.5);
    static final TestLargeRecord testLargeRec1 =
        new TestLargeRecord((byte) 1, (short) 2, 3, 4L, 1.0f, 2.0, true);
    static final TestLargeRecord testLargeRec2 =
        new TestLargeRecord((byte) -1, (short) -2, -3, -4L, -1.0f, -2.0, false);
    static final TestEnum testEnum1 = new TestEnum.One(-1, 0L);
    static final TestEnum testEnum2 = new TestEnum.Two(1.5);
    static final int[] testVec1 = new int[]{0, 1};
    static final int[] testVec2 = new int[]{2, 4, 6};
    static final byte[] testBytes = new byte[256];
    static final int[] testPrimitiveList = new int[1025];
    static final List<TestRecord> testRecordList = java.util.stream.IntStream
        .rangeClosed(0, 1024)
        .mapToObj(i -> new TestRecord(i, (long) i * 2, i / 2.0))
        .toList();
    static {
        for (int i = 0; i < testBytes.length; i++) {
            testBytes[i] = (byte) i;
        }
        for (int i = 0; i < testPrimitiveList.length; i++) {
            testPrimitiveList[i] = i;
        }
    }
    static final Map<Integer, Integer> testMap1 = Map.of(0, 1, 1, 2);
    static final Map<Integer, Integer> testMap2 = Map.of(2, 4);
    static final TestInterface testInterface = new TestInterface();
    static final TestInterface testInterface2 = new TestInterface();
    static final TestTraitInterface testTraitInterface = Benchmarks.makeTestTraitInterface();
    static final TestTraitInterface testTraitInterface2 = Benchmarks.makeTestTraitInterface();
    static final NestedData testNestedData1 = new NestedData(
        List.of(new TestRecord(-1, 1L, 1.5)),
        List.of(List.of("one", "two"), List.of("three")),
        Map.of(
            "one", new TestEnum.One(-1, 1L),
            "two", new TestEnum.Two(0.5)
        )
    );
    static final NestedData testNestedData2 = new NestedData(
        List.of(new TestRecord(-2, 2L, 4.5)),
        List.of(List.of("four", "five")),
        Map.of("two", new TestEnum.Two(-0.5))
    );
}

class TestCallbackObj implements TestCallbackInterface {
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
    public TestLargeRecord largeRecords(TestLargeRecord a, TestLargeRecord b) {
        return new TestLargeRecord(
            (byte) (a.a() + b.a()),
            (short) (a.b() + b.b()),
            a.c() + b.c(),
            a.d() + b.d(),
            a.e() + b.e(),
            a.f() + b.f(),
            a.g() && b.g()
        );
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
    public int[] vecSmall(int[] a, int[] b) {
        int[] result = new int[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @Override
    public int[] vecPrimitives(int[] v) {
        return v;
    }

    @Override
    public List<TestRecord> vecRecords(List<TestRecord> v) {
        return v;
    }

    @Override
    public int optionals(Integer a, Boolean b, String c) {
        int sum = a == null ? 0 : a;
        if (Boolean.TRUE.equals(b)) {
            sum *= 2;
        }
        if (c != null) {
            sum += c.length();
        }
        return sum;
    }

    @Override
    public byte[] bytes(byte[] v) {
        return v;
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
                    Benchmarks.testCaseLargeStrings(TestData.testLargeString1, TestData.testLargeString2);
                }
            }
            case RECORDS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseRecords(TestData.testRec1, TestData.testRec2);
                }
            }
            case ENUMS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseEnums(TestData.testEnum1, TestData.testEnum2);
                }
            }
            case LARGE_RECORDS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseLargeRecords(TestData.testLargeRec1, TestData.testLargeRec2);
                }
            }
            case OPTIONALS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseOptionals(10, null, "testing-123");
                }
            }
            case BYTES -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseBytes(TestData.testBytes);
                }
            }
            case VEC_SMALL -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseVecSmall(TestData.testVec1, TestData.testVec2);
                }
            }
            case VEC_PRIMITIVES -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseVecPrimitives(TestData.testPrimitiveList);
                }
            }
            case VEC_RECORDS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseVecRecords(TestData.testRecordList);
                }
            }
            case METHODS -> {
                for (long i = 0; i < count; i++) {
                    TestData.testInterface.noopMethod();
                }
            }
            case HASHMAPS -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseHashmaps(TestData.testMap1, TestData.testMap2);
                }
            }
            case INTERFACES -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseInterfaces(TestData.testInterface, TestData.testInterface2);
                }
            }
            case TRAIT_INTERFACES -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseTraitInterfaces(TestData.testTraitInterface, TestData.testTraitInterface2);
                }
            }
            case NESTED_DATA -> {
                for (long i = 0; i < count; i++) {
                    Benchmarks.testCaseNestedData(TestData.testNestedData1, TestData.testNestedData2);
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
            // Without this an unhandled case reports ~0ns rather than failing, which is how this
            // runner silently drifted behind the fixture across a uniffi upgrade.
            default -> throw new IllegalStateException("unhandled TestCase: " + testCase);
        }
        return System.nanoTime() - start;
    }
}

public class RunBenchmarks {
    public static void main(String[] args) {
        Benchmarks.runBenchmarks("java", new TestCallbackObj());
    }
}
