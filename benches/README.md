# Benchmarks

Criterion-based benchmarks measuring FFI call overhead for the generated Java bindings. Uses the same `uniffi-fixture-benchmarks` fixture and benchmark structure as upstream [uniffi-rs](https://github.com/mozilla/uniffi-rs/tree/main/fixtures/benchmarks), making results directly comparable with Kotlin, Python, and Swift.

## Prerequisites

Rust toolchain, JDK 21+, and JNA on the `CLASSPATH` — all provided by `nix develop`.

## Running

```bash
cargo bench                             # full suite
cargo bench -- call-only                # filter by name
cargo bench -- --save-baseline before   # save a Criterion baseline
cargo bench -- --load-baseline before   # compare against a saved baseline
```

## How It Works

`cargo bench` runs `benches/benchmarks.rs` which:

1. Builds the `uniffi-fixture-benchmarks` cdylib (opt-level 3)
2. Generates Java bindings and packages them into a jar
3. Compiles `benches/bindings/RunBenchmarks.java`
4. Runs the Java process, which calls `Benchmarks.runBenchmarks("java", callback)`

Criterion runs inside the Rust fixture library. The Java side implements `TestCallbackInterface` and provides timing for function-call benchmarks via `runTest()` (using `System.nanoTime()`). Callback benchmarks are timed directly by Criterion on the Rust side.

## Benchmark Groups

- **function-calls** — Java calling Rust functions across 12 type categories
- **callbacks** — Rust calling Java callback methods across the same 12 categories

## Results

HTML reports are written to `target/criterion/`. Raw data persists across runs for regression detection.

### Pre-results

A run of the benchmarks (and comparison to upstream) on March 26, 2026 with an M4 Max 2024 Macbook Pro, 36GB memory. There was an upstream error in Python's `nested-data` bench at this time. All calls are in microseconds unless noted.

#### Function Calls (foreign code calling Rust)

| Test Case          |     Java |   Kotlin |   Python |    Swift |
|--------------------|----------|----------|----------|----------|
| call-only          |      2.1 |      2.8 |   810 ns |   172 ns |
| primitives         |      1.8 |      3.0 |      1.4 |   195 ns |
| strings            |     14.7 |     16.8 |      9.2 |   973 ns |
| large-strings      |     15.9 |     21.5 |     12.5 |      1.3 |
| records            |     14.5 |     16.6 |     18.9 |      2.6 |
| enums              |     12.6 |     16.7 |     14.6 |      2.1 |
| vecs               |     15.6 |     17.2 |     18.3 |      4.4 |
| hash-maps          |     12.3 |     16.9 |     23.4 |      6.3 |
| interfaces         |      8.6 |     13.8 |      4.3 |   396 ns |
| trait-interfaces   |      9.2 |     12.8 |      4.4 |   461 ns |
| nested-data        |     13.5 |     17.9 |      --- |     20.6 |
| errors             |      4.7 |      7.4 |      3.2 |   642 ns |

#### Callbacks (Rust calling foreign code)

| Test Case          |     Java |   Kotlin |   Python |    Swift |
|--------------------|----------|----------|----------|----------|
| call-only          |      2.6 |      3.2 |   477 ns |   121 ns |
| primitives         |      4.3 |      3.9 |   793 ns |   166 ns |
| strings            |     13.5 |     18.0 |      8.0 |   811 ns |
| large-strings      |     18.2 |     22.6 |     10.7 |      1.6 |
| records            |     16.6 |     17.5 |     14.0 |      2.8 |
| enums              |     17.0 |     17.5 |     11.0 |      2.4 |
| vecs               |     16.9 |     17.9 |     18.1 |      4.8 |
| hash-maps          |     12.7 |     18.1 |     21.4 |      7.1 |
| interfaces         |      7.8 |     35.6 |      4.1 |   429 ns |
| trait-interfaces   |     11.1 |     27.2 |      4.3 |   528 ns |
| nested-data        |     21.8 |     16.8 |      --- |     24.1 |
| errors             |     10.4 |      8.9 |      4.3 |   566 ns |
