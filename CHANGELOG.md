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

