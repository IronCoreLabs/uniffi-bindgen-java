/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package uniffi.benchmarks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class FunctionCallBenchmarks {

    private String testLargeString1;
    private String testLargeString2;
    private TestRecord testRec1;
    private TestRecord testRec2;
    private TestEnum testEnum1;
    private TestEnum testEnum2;
    private int[] testVec1;
    private int[] testVec2;
    private Map<Integer, Integer> testMap1;
    private Map<Integer, Integer> testMap2;
    private TestInterface testInterface1;
    private TestInterface testInterface2;
    private TestTraitInterface testTraitInterface1;
    private TestTraitInterface testTraitInterface2;
    private NestedData testNestedData1;
    private NestedData testNestedData2;

    @Setup
    public void setup() {
        testLargeString1 = "a".repeat(2048);
        testLargeString2 = "b".repeat(1500);
        testRec1 = new TestRecord(-1, 1L, 1.5);
        testRec2 = new TestRecord(-2, 2L, 4.5);
        testEnum1 = new TestEnum.One(-1, 0L);
        testEnum2 = new TestEnum.Two(1.5);
        testVec1 = new int[]{0, 1};
        testVec2 = new int[]{2, 4, 6};
        testMap1 = Map.of(0, 1, 1, 2);
        testMap2 = Map.of(2, 4);
        testInterface1 = new TestInterface();
        testInterface2 = new TestInterface();
        testTraitInterface1 = Benchmarks.makeTestTraitInterface();
        testTraitInterface2 = Benchmarks.makeTestTraitInterface();
        testNestedData1 = new NestedData(
            List.of(new TestRecord(-1, 1L, 1.5)),
            List.of(List.of("one", "two"), List.of("three")),
            Map.of(
                "one", new TestEnum.One(-1, 1L),
                "two", new TestEnum.Two(0.5)
            )
        );
        testNestedData2 = new NestedData(
            List.of(new TestRecord(-2, 2L, 4.5)),
            List.of(List.of("four", "five")),
            Map.of("two", new TestEnum.Two(-0.5))
        );
    }

    @TearDown
    public void teardown() {
        testInterface1.close();
        testInterface2.close();
    }

    @Benchmark
    public void callOnly() {
        Benchmarks.testCaseCallOnly();
    }

    @Benchmark
    public double primitives() {
        return Benchmarks.testCasePrimitives((byte) 0, -1);
    }

    @Benchmark
    public String strings() {
        return Benchmarks.testCaseStrings("a", "b");
    }

    @Benchmark
    public String largeStrings() {
        return Benchmarks.testCaseLargeStrings(testLargeString1, testLargeString2);
    }

    @Benchmark
    public TestRecord records() {
        return Benchmarks.testCaseRecords(testRec1, testRec2);
    }

    @Benchmark
    public TestEnum enums() {
        return Benchmarks.testCaseEnums(testEnum1, testEnum2);
    }

    @Benchmark
    public int[] vecs() {
        return Benchmarks.testCaseVecs(testVec1, testVec2);
    }

    @Benchmark
    public Map<Integer, Integer> hashmaps() {
        return Benchmarks.testCaseHashmaps(testMap1, testMap2);
    }

    @Benchmark
    public TestInterface interfaces() {
        return Benchmarks.testCaseInterfaces(testInterface1, testInterface2);
    }

    @Benchmark
    public TestTraitInterface traitInterfaces() {
        return Benchmarks.testCaseTraitInterfaces(testTraitInterface1, testTraitInterface2);
    }

    @Benchmark
    public NestedData nestedData() {
        return Benchmarks.testCaseNestedData(testNestedData1, testNestedData2);
    }

    @Benchmark
    public void errors(Blackhole bh) {
        try {
            bh.consume(Benchmarks.testCaseErrors());
        } catch (TestException e) {
            bh.consume(e);
        }
    }
}
