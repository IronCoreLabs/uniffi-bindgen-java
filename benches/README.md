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
