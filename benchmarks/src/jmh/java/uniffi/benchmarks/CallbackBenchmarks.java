/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package uniffi.benchmarks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class CallbackBenchmarks {

    private JavaCallbackImpl callback;

    @Setup
    public void setup() {
        callback = new JavaCallbackImpl();
    }

    @Benchmark
    public void callOnly() {
        Benchmarks.runCallbackTest(callback, TestCase.CALL_ONLY, 1L);
    }

    @Benchmark
    public void primitives() {
        Benchmarks.runCallbackTest(callback, TestCase.PRIMITIVES, 1L);
    }

    @Benchmark
    public void strings() {
        Benchmarks.runCallbackTest(callback, TestCase.STRINGS, 1L);
    }

    @Benchmark
    public void largeStrings() {
        Benchmarks.runCallbackTest(callback, TestCase.LARGE_STRINGS, 1L);
    }

    @Benchmark
    public void records() {
        Benchmarks.runCallbackTest(callback, TestCase.RECORDS, 1L);
    }

    @Benchmark
    public void enums() {
        Benchmarks.runCallbackTest(callback, TestCase.ENUMS, 1L);
    }

    @Benchmark
    public void vecs() {
        Benchmarks.runCallbackTest(callback, TestCase.VECS, 1L);
    }

    @Benchmark
    public void hashmaps() {
        Benchmarks.runCallbackTest(callback, TestCase.HASHMAPS, 1L);
    }

    @Benchmark
    public void interfaces() {
        Benchmarks.runCallbackTest(callback, TestCase.INTERFACES, 1L);
    }

    @Benchmark
    public void traitInterfaces() {
        Benchmarks.runCallbackTest(callback, TestCase.TRAIT_INTERFACES, 1L);
    }

    @Benchmark
    public void nestedData() {
        Benchmarks.runCallbackTest(callback, TestCase.NESTED_DATA, 1L);
    }

    @Benchmark
    public void errors() {
        Benchmarks.runCallbackTest(callback, TestCase.ERRORS, 1L);
    }
}
