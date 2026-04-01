# Benchmarks

Criterion-based benchmarks measuring FFI call overhead for the generated Java bindings. Uses the same `uniffi-fixture-benchmarks` fixture and benchmark structure as upstream [uniffi-rs](https://github.com/mozilla/uniffi-rs/tree/main/fixtures/benchmarks), making results directly comparable with Kotlin, Python, and Swift.

## Prerequisites

Rust toolchain and JDK 21+ — all provided by `nix develop`.

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

### Comparison: Java FFM vs JNA and upstream languages

April 1, 2026 on an M4 Max 2024 MacBook Pro, 36GB memory. Java FFM uses JDK 25 with the Foreign Function & Memory API. Java JNA uses JDK 21. Kotlin, Python, and Swift results are from upstream `uniffi-rs` main.

#### Function Calls (foreign code calling Rust)

| Test Case        | Java FFM | Java JNA  | Kotlin    | Python    | Swift     |
|------------------|----------|-----------|-----------|-----------|-----------|
| call-only        | 4.3 ns   | 1.83 us   | 2.89 us   | 792 ns    | 165 ns    |
| primitives       | 4.5 ns   | 1.62 us   | 3.27 us   | 1.39 us   | 191 ns    |
| strings          | 324 ns   | 11.46 us  | 15.08 us  | 9.06 us   | 929 ns    |
| large-strings    | 3.69 us  | 15.63 us  | 21.69 us  | 12.33 us  | 1.28 us   |
| records          | 371 ns   | 11.62 us  | 15.29 us  | 18.66 us  | 2.54 us   |
| enums            | 334 ns   | 14.47 us  | 15.41 us  | 14.22 us  | 2.04 us   |
| vecs             | 581 ns   | 13.14 us  | 15.36 us  | 18.02 us  | 4.55 us   |
| hash-maps        | 638 ns   | 12.91 us  | 15.48 us  | 23.15 us  | 6.39 us   |
| interfaces       | 530 ns * | 6.07 us   | 105.60 us | 4.21 us   | 409 ns    |
| trait-interfaces | 532 ns * | 11.98 us  | 150.70 us | 4.30 us   | 477 ns    |
| nested-data      | 1.90 us  | 12.56 us  | 15.64 us  | 77.09 us  | 20.86 us  |
| errors           | 682 ns   | 4.51 us   | 6.44 us   | 3.20 us   | 646 ns    |

#### Callbacks (Rust calling foreign code)

| Test Case        | Java FFM | Java JNA  | Kotlin    | Python    | Swift     |
|------------------|----------|-----------|-----------|-----------|-----------|
| call-only        | 55 ns    | 1.98 us   | 2.76 us   | 492 ns    | 121 ns    |
| primitives       | 59 ns    | 2.66 us   | 3.40 us   | 804 ns    | 166 ns    |
| strings          | 348 ns   | 13.76 us  | 15.69 us  | 8.17 us   | 811 ns    |
| large-strings    | 3.84 us  | 17.47 us  | 20.55 us  | 10.77 us  | 1.60 us   |
| records          | 449 ns   | 11.55 us  | 15.95 us  | 14.26 us  | 2.86 us   |
| enums            | 406 ns   | 11.17 us  | 14.93 us  | 11.04 us  | 2.47 us   |
| vecs             | 593 ns   | 11.11 us  | 15.02 us  | 17.95 us  | 5.01 us   |
| hash-maps        | 723 ns   | 12.36 us  | 15.72 us  | 21.39 us  | 7.16 us   |
| interfaces       | 964 ns * | 7.44 us   | 408.65 us | 4.19 us   | 448 ns    |
| trait-interfaces | 790 ns * | 7.49 us   | 361.69 us | 4.33 us   | 552 ns    |
| nested-data      | 1.93 us  | 19.77 us  | 16.29 us  | 67.20 us  | 24.54 us  |
| errors           | 638 ns   | 6.37 us   | 7.98 us   | 4.35 us   | 574 ns    |

\* Interface benchmarks have high variance due to GC pauses; min values are ~250-470ns.
