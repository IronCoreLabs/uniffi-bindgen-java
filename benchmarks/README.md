# JMH Benchmarks

Microbenchmarks for uniffi-bindgen-java using [JMH](https://openjdk.org/projects/code-tools/jmh/) (Java Microbenchmark Harness). These measure FFI call overhead, type serialization costs, and callback performance for the generated Java bindings.

## Prerequisites

- Rust toolchain (1.87.0+)
- JDK 21+
- JNA (on the `CLASSPATH`)
- Gradle

All of these are provided by `nix develop` from the project root.

## Running

```bash
# Full benchmark suite (default: 2 forks, 3 warmup iters, 5 measurement iters)
cargo bench --bench benchmarks

# Quick smoke test
cargo bench --bench benchmarks -- --jmh-args="-f 1 -wi 1 -i 1"

# Filter to specific benchmarks
cargo bench --bench benchmarks -- --jmh-args="FunctionCall"
cargo bench --bench benchmarks -- --jmh-args="Callback"

# Combine options
cargo bench --bench benchmarks -- --jmh-args="-f 1 -wi 2 -i 3 FunctionCall"
```

### JMH Args Reference

| Flag  | Meaning              | Default |
| ----- | -------------------- | ------- |
| `-f`  | Number of forks      | 2       |
| `-wi` | Warmup iterations    | 3       |
| `-i`  | Measurement iterations | 5     |
| `-w`  | Warmup time          | 1s      |
| `-r`  | Measurement time     | 1s      |

Positional args filter benchmark names (substring match).

## How It Works

The `cargo bench` harness (`benches/benchmarks.rs`):

1. Builds the `uniffi-fixture-benchmarks` Rust cdylib (with `opt-level = 3`)
2. Generates Java bindings into `benchmarks/build/generated-sources/uniffi/`
3. Copies the native library into `benchmarks/build/native-resources/{os}-{arch}/`
4. Runs JMH via Gradle, which compiles the generated bindings + hand-written JMH benchmarks together

## Results

Results are written to `benchmarks/build/results/jmh/results.txt` after each run.

## Benchmark Classes

- **`FunctionCallBenchmarks`** — Measures Rust function call overhead across type categories: primitives, strings, records, enums, vecs, hashmaps, interfaces, errors, nested data
- **`CallbackBenchmarks`** — Measures Rust→Java callback overhead for the same type categories
- **`JavaCallbackImpl`** — Java callback implementations used by the callback benchmarks

## Comparing with Upstream Kotlin/Python/Swift Benchmarks

The upstream [uniffi-rs](https://github.com/mozilla/uniffi-rs) repo includes Criterion-based benchmarks for Kotlin, Python, and Swift using the same `uniffi-fixture-benchmarks` fixture. Run them from `fixtures/benchmarks/` in that repo:

```bash
cargo bench -- -k   # Kotlin
cargo bench -- -p   # Python
cargo bench -- -s   # Swift
```

**Function call benchmarks are directly comparable.** Both JMH and Criterion's `function-calls` group measure the same thing: foreign code calling Rust `testCase*` functions. The test data and Rust functions are identical.

**Callback benchmarks measure differently.** The upstream Criterion benchmarks time callbacks from the Rust side — Rust calls `run_callback_test()` which invokes the foreign callback, and Criterion measures that. Our JMH benchmarks time from the Java side — JMH measures a Java→Rust call to `runCallbackTest()` which then calls back into Java. This adds an extra FFI round trip (the initial Java→Rust hop) that the upstream benchmarks don't include, so our callback numbers will be higher by roughly one bare FFI call (~2-3µs).

## Adding Benchmarks

1. Add JMH-annotated benchmark classes in `src/jmh/java/uniffi/benchmarks/`
2. Reference the generated bindings from `uniffi.benchmarks.*`
3. The Rust fixture lives in the upstream [uniffi-rs](https://github.com/mozilla/uniffi-rs) repo as `uniffi-fixture-benchmarks`
