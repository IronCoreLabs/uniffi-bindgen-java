## 0.5.2

- fix a narrow race in async cancellation. If `cancel()` on a `CompletableFuture` returned by an async function landed in the few instructions between the pipeline's `isCancelled()` check and its own `rust_future_free`, or between a wake and the re-poll it triggers, the Rust future was freed twice or polled after being freed. Seen once in CI as a glibc `tcache_thread_shutdown()` abort. Every use of the handle now runs under the future's monitor and stops once it has been freed. No measurable change to async call overhead.
- fix generated Java failing to compile when a Rust field or parameter name matched a name the generator used in the same scope, such as an enum variant field named `value` ([#63](https://github.com/IronCoreLabs/uniffi-bindgen-java/issues/63)). Enum and error variant field names, and function, method, constructor and callback-interface parameter names can no longer collide with anything the Java generator emits. Names uniffi-rs itself reserves on the Rust side, such as a field named `buf` or a callback-interface parameter named `uniffi_handle`, are rejected by the Rust derive before bindings are generated and remain unavailable.

## 0.5.1

- adjust dependency requirements to allow consumption of `uniffi` patch bumps

## 0.5.0

- updated to UniFFI 0.32.0 (and Askama 0.16).
- added support for `HashSet`, which UniFFI 0.32 exposes to proc-macros. Rust sets map to `java.util.Set`, preserving insertion order on the way back from Rust.
- added zero-copy `&[u8]` / `[ByRef] bytes` arguments. Rust borrows the caller's buffer for the duration of the call instead of copying it into a `RustBuffer`, which also removes the separate FFI round-trip that allocating that buffer required. Measured 3x (64B) to 20x (1MB) faster than the owned path. Synchronous foreign-to-Rust and argument position only.

### Breaking

- `--config` now expects a UniFFI [global config file](https://mozilla.github.io/uniffi-rs/next/bindings.html#global-configuration) with `[defaults]`, `[crates.<name>]` and/or `[crate-roots]` sections, rather than a flat `uniffi.toml`-style override. Old-style files are ignored with a warning.
- `&[u8]` / `[ByRef] bytes` arguments now take a **direct** `java.nio.ByteBuffer` rather than `byte[]`, matching Kotlin and Swift. Migrate with `ByteBuffer.allocateDirect(arr.length).put(arr).flip()`; a heap buffer throws `IllegalArgumentException`. Reuse the buffer across calls where you can - allocating a direct buffer per call is slower than reusing one, though still well ahead of the old owned path. Rust reads the buffer during the call, so it must not be mutated by another thread meanwhile.

## 0.4.2

- Added `nullness_annotations` config option to emit JSpecify `@NullMarked` and
  `@Nullable` annotations in generated code. When enabled, Rust `Option<T>` maps to
  `@Nullable T` and all other types are non-null by default. Requires
  `org.jspecify:jspecify` on the compile classpath.

## 0.4.1

- fix resolving callback trait implementations declared in submodules of the current crate. Previously, traits like `my_crate::metrics::MetricsRecorder` failed with "no interface with module_path" during code generation.
- fix Java keyword collisions in generated callback helper class names. Callback methods named after Java keywords (e.g., `record`) no longer produce invalid nested type declarations. Thanks @criccomini for this and the above fix!
- fix `libraryOverride` system property to work with absolute paths. Previously, passing an absolute path via `-Duniffi.component.<namespace>.libraryOverride=/path/to/lib.so` would fail because it was always passed to `System.loadLibrary()`. Now absolute paths are correctly routed to `System.load()`.

## 0.4.0

- switched from JNA generated bindings to FFM ones. Benchmarked performance speedup (via upstream benchmark suite) is from 4.2x-426x.

### Breaking

- the "major" (pre-release) bump is primarily because the switch from JNA to FFM is so foundational.
- Java 22+ is required (though 21+ could work using preview features).

## 0.3.1

- remove spinlock where Java checks for Rust future completion (uses a thenCompose based CF chain to prevent unnecessary blocking)
- generate overloads for async functions that accept and use a custom Executor

## 0.3.0

- update to uniffi 0.31.0
- switched to the new BindgenPaths API for generation internally
- support methods on records and enums
- multiple bugfixes ported from Kotlin changes
- fully qualified all `java.lang` type use in templates to simplify imports and avoid potential name collisions
- added `omit_checksums` config option, see [the kotlin doc about it](https://mozilla.github.io/uniffi-rs/latest/kotlin/configuration.html#available-options).
- support the `rename` config option, see [the docs](https://mozilla.github.io/uniffi-rs/latest/renaming.html).
- Uniffi trait methods
- don't strip prefix on Error names [#38](https://github.com/IronCoreLabs/uniffi-bindgen-java/pull/48)

### Breaking

- method checksums change (since we skipped over 0.30.0 this is only theoretically breaking)
- `--lib-file` and `--library` CLI generator options removed, they're both now automatically detected by uniffi
- Java callback interface implementations must now use primitive types (e.g., `int`, `long`, `boolean`) instead of boxed types (`Integer`, `Long`, `Boolean`) for non-optional primitive parameters and return types
- use Java primitive types (`int`, `long`, `boolean`, etc.) instead of boxed types (`Integer`, `Long`, `Boolean`) for non-optional primitive parameters, return types, and record fields. Optional primitives and primitives in generic contexts (e.g., `List<Integer>`, `CompletableFuture<Integer>`) still use boxed types as required by Java.
- use primitive arrays where possible (similar to how `Vec<u8> -> bytes[]`). This should reduce GC pressure and speed up copies across the boundary via `AsBuffer` instead of iteration. There's a potentially small ergonomic hit to those doing a lot of data transformation, not just passing values to and from the Rust side. If you're someone this negatively impacts, reach out and we can talk about adding a `use_primitive_arrays` config option.

## 0.2.1

- Fix `OptionalTemplate` missing potential imports of `java.util.List` and `java.util.Map`

## 0.2.0

- Update to uniffi 0.29.2
- Consumes a uniffi fix to Python bindgen that was affecting `ironcore-alloy` (PR #2512)

Consumers will also need to update to uniffi 0.29.2.

## 0.1.1

- Update to uniffi 0.29.1
- Fix a potential memory leak in arrays and maps reported in uniffi proper. A similar fix to the one for Kotlin applied here.

Consumers will also need to update to uniffi 0.29.1, but there will be no changes required to their code.

## 0.1.0

Initial pre-release. This library will be used to provide Java bindings for [IronCore Alloy](https://github.com/IronCoreLabs/ironcore-alloy/tree/main). It will recieve frequent breaking changes initially as we find improvements through that libaries' usage of it.
