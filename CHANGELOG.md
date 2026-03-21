## 0.3.0

- update to uniffi 0.31.0
- switched to the new BindgenPaths API for generation internally
- support methods on records and enums
- multiple bugfixes ported from Kotlin changes
- fully qualified all `java.lang` type use in templates to simplify imports and avoid potential name collisions
- added `omit_checksums` config option
- Uniffi trait methods

### Breaking

- method checksums change (since we skipped over 0.30.0 this is only theoretically breaking)
- `--lib-file` and `--library` CLI generator options removed, they're both now automatically detected by uniffi

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

