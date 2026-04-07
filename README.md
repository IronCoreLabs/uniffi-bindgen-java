# uniffi-bindgen-java

Generate [UniFFI](https://github.com/mozilla/uniffi-rs) bindings for Java.

Official Kotlin bindings already exist, which can be used by any JVM language including Java. The Java specific bindings use Java-native types where possible for a more ergonomic interface, for example the Java bindings use `CompletableFutures` instead of `kotlinx.coroutines`.

Generated bindings use Java's [Foreign Function & Memory API](https://docs.oracle.com/en/java/javase/21/core/foreign-function-and-memory-api.html) (Project Panama) instead of JNA. See [benches/](benches/) for performance comparisons with Kotlin, Python, and Swift.

We highly reccommend you use [UniFFI's proc-macro definition](https://mozilla.github.io/uniffi-rs/latest/proc_macro/index.html) instead of UDL where possible. 

## Requirements

* Java 22+: `javac`, and `jar`
* At runtime, the JVM must be allowed to use the Foreign Function & Memory API. For classpath-based applications, pass `--enable-native-access=ALL-UNNAMED` to `java`. For JPMS modules, use `--enable-native-access=your.module.name`. See [Java's documentation](https://docs.oracle.com/en/java/javase/25/core/restricted-methods.html#GUID-080FE2FA-F96A-4987-B4E1-A9F089D11B54__GUID-70A202F4-46C0-4D4D-8CD0-9D147854F776) for more information.

## Installation

MSRV is `1.87.0`.

`cargo install uniffi-bindgen-java --git https://github.com/IronCoreLabs/uniffi-bindgen-java`

## Usage

```
uniffi-bindgen-java --help
Java scaffolding and bindings generator for Rust

Usage:

Commands:
  generate     Generate Java bindings
  scaffolding  Generate Rust scaffolding code
  print-repr   Print a debug representation of the interface from a dynamic library

Options:
  -h, --help     Print help
  -V, --version  Print version
```

### Generate Bindings

```
uniffi-bindgen-java generate --help
Generate Java bindings

Usage:

Arguments:
  <SOURCE>  Path to the UDL file or compiled library (.so, .dll, .dylib, or .a)

Options:
  -o, --out-dir <OUT_DIR>   Directory in which to write generated files. Default is same folder as .udl file
  -n, --no-format           Do not try to format the generated bindings
  -c, --config <CONFIG>     Path to optional uniffi config file. This config is merged with the `uniffi.toml` config present in each crate, with its values taking precedence
      --crate <CRATE_NAME>  When a library is passed as SOURCE, only generate bindings for this crate. When a UDL file is passed, use this as the crate name instead of attempting to locate and parse Cargo.toml
      --metadata-no-deps    Whether we should exclude dependencies when running "cargo metadata". This will mean external types may not be resolved if they are implemented in crates outside of this workspace. This can be used in environments when all types are in the namespace and fetching all sub-dependencies causes obscure platform specific problems
  -h, --help                Print help
  -V, --version             Print version
```

As an example:

```
> git clone https://github.com/mozilla/uniffi-rs.git
> cd uniffi-rs/examples/arithmetic-proc-macro
> cargo b --release
> uniffi-bindgen-java generate --out-dir ./generated-java ../../target/release/libarithmeticpm.so
> ll generated-java/uniffi/arithmeticpm/
total 216
-rw-r--r-- 1 user users  295 Jul 24 13:02 ArithmeticExceptionErrorHandler.java
-rw-r--r-- 1 user users  731 Jul 24 13:02 ArithmeticException.java
-rw-r--r-- 1 user users 3126 Jul 24 13:02 Arithmeticpm.java
-rw-r--r-- 1 user users  512 Jul 24 13:02 AutoCloseableHelper.java
-rw-r--r-- 1 user users  584 Jul 24 13:02 FfiConverterBoolean.java
...
> cat generated-java/uniffi/arithmeticpm/Arithmeticpm.java
package uniffi.arithmeticpm;


import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
public class Arithmeticpm {
  public static long add(long a, long b) throws ArithmeticException {
            try {
...

```

### Generate Scaffolding

```
uniffi-bindgen-java scaffolding --help
Generate Rust scaffolding code

Usage:

Arguments:
  <UDL_FILE>  Path to the UDL file

Options:
  -o, --out-dir <OUT_DIR>  Directory in which to write generated files. Default is same folder as .udl file
  -n, --no-format          Do not try to format the generated bindings
```

### Print Debug Representation

```
uniffi-bindgen-java print-repr --help
Print a debug representation of the interface from a dynamic library

Usage:

Arguments:
  <PATH>  Path to the library file (.so, .dll, .dylib, or .a)
```

## Integrating Bindings

After generation you'll have an `--out-dir` full of Java files. Package those into a `.jar` using your build tools of choice, and the result can be imported and used as per normal in any Java project. The generated code uses the Foreign Function & Memory API (no external dependencies like JNA are required).

Any top level functions in the Rust library will be static methods in a class named after the crate.

## Configuration

The generated Java can be configured using a `uniffi.toml` configuration file.

| Configuration name | Default | Description |
| --- | --- | --- |
| `package_name` | `uniffi` | The Java package name - ie, the value use in the `package` statement at the top of generated files. |
| `cdylib_name` | `uniffi_{namespace}` | The name of the compiled Rust library containing the FFI implementation (not needed when using `generate --library`) |
| `generate_immutable_records` | `false` | Whether to generate records with immutable fields (`record` instead of `class`). |
| `custom_types` | | A map which controls how custom types are exposed to Java. See the [custom types section of the UniFFI manual](https://mozilla.github.io/uniffi-rs/latest/udl/custom_types.html#custom-types-in-the-bindings-code) |
| `external_packages` | | A map of packages to be used for the specified external crates. The key is the Rust crate name, the value is the Java package which will be used referring to types in that crate. See the [external types section of the manual](https://mozilla.github.io/uniffi-rs/latest/udl/ext_types_external.html#kotlin) |
| `rename` | | A map to rename types, functions, methods, and their members in the generated Java bindings. See the [renaming section](https://mozilla.github.io/uniffi-rs/latest/renaming.html). |
| `nullness_annotations` | `false` | Generate [JSpecify](https://jspecify.dev/) nullness annotations. Rust `Option<T>` maps to `@Nullable T`; all other types are non-null by default via `@NullMarked`. Requires `org.jspecify:jspecify` on the compile classpath. See [Nullness Annotations](#nullness-annotations). |
| `android` | `false` | Generate [PanamaPort](https://github.com/vova7878/PanamaPort)-compatible code for Android. Replaces `java.lang.foreign.*` with `com.v7878.foreign.*` and `java.lang.invoke.VarHandle` with `com.v7878.invoke.VarHandle`. Requires PanamaPort `io.github.vova7878.panama:Core` as a runtime dependency and Android API 26+. |
| `omit_checksums` | `false` | Whether to omit checking the library checksums as the library is initialized. Changing this will shoot yourself in the foot if you mixup your build pipeline in any way, but might speed up initialization. |

### Example

#### Custom types

```
[bindings.java]
package_name = "customtypes"

[bindings.java.custom_types.Url]
# Name of the type in the Java code
type_name = "URL"
# Classes that need to be imported
imports = ["java.net.URI", "java.net.URL"]
# Functions to convert between strings and URLs
lift = "new URI({}).toURL()"
lower = "{}.toString()"
```

#### External Types

```
[bindings.java.external_packages]
# This specifies that external types from the crate `rust-crate-name` will be referred by by the package `"java.package.name`.
rust-crate-name = "java.package.name"
```

## Library Loading

By default, the generated code uses `System.loadLibrary()` to find the native library via `java.library.path`. If your native library lives outside the standard search paths or automatic discovery doesn't work for your environment, you can specify an absolute path at runtime using a system property:

```
java -Duniffi.component.<namespace>.libraryOverride=/path/to/libmylib.so ...
```

Where `<namespace>` is the UniFFI namespace of your component (e.g., `arithmetic`). When the override is an absolute path, the generated code uses `System.load()` instead of `System.loadLibrary()`, bypassing `java.library.path` entirely.

You can also pass a plain library name as the override, in which case it behaves like `System.loadLibrary()` and still requires the library to be on `java.library.path`.

## Nullness Annotations

Generated bindings can include [JSpecify](https://jspecify.dev/) nullness annotations so that
Kotlin consumers get proper nullable/non-null types and Java consumers get IDE and static
analysis support.

Enable in `uniffi.toml`:

```toml
[bindings.java]
nullness_annotations = true
```

When enabled:
- A `package-info.java` is generated with `@NullMarked`, making all types non-null by default
- Rust `Option<T>` types are annotated with `@Nullable`, including inside generic type
  arguments (e.g., `Map<String, @Nullable Integer>` for `HashMap<String, Option<i32>>`)
- All non-optional types (primitives, strings, records, objects, enums) are non-null

### Build Setup

JSpecify must be on the compile classpath when compiling the generated Java source.

**Gradle:**
```kotlin
// Use `api` if publishing a library so Kotlin/Java consumers benefit automatically.
// Use `compileOnly` if the bindings are only used within this project.
dependencies {
    api("org.jspecify:jspecify:1.0.0")
}
```

**Maven:**
```xml
<!-- Use default scope if publishing a library. Use <scope>provided</scope> for internal use. -->
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
    <version>1.0.0</version>
</dependency>
```

There is no runtime dependency — the JVM ignores annotation classes that are not present at
runtime.

### Kotlin Interop

Without nullness annotations, Kotlin sees all Java types from the generated bindings as
**platform types** (`String!`), which bypass null-safety checks. With annotations enabled,
Kotlin correctly maps:

- Non-optional types → non-null (`String`, `MyRecord`)
- `Option<T>` types → nullable (`String?`, `MyRecord?`)

This requires JSpecify to be on Kotlin's compile classpath (automatic if declared with `api`
scope).

## Notes

- failures in CompletableFutures will cause them to `completeExceptionally`. The error that caused the failure can be checked with `e.getCause()`. When implementing an async Rust trait in Java, you'll need to `completeExceptionally` instead of throwing. See `TestFixtureFutures.java` for an example trait implementation with errors.
- all primitives are signed in Java by default. Rust correctly interprets the a signed primitive value from Java as unsigned when told to. Callers of Uniffi functions need to be aware when making comparisons (`compareUnsigned`) or printing when a value is actually unsigned to code around footguns on this side.
- this is an internal note for development but because Enum variants are not cases/hanging off their parent in Java, their named standalone, they can conflict with any/all `java.lang` types. We could do extensive checking and forced renaming around this, but instead we use fully qualified names for all `java.lang` types in all templates. Ensure that when you're making changes you're not dropping those qualified names or adding generated code without them.


## Unsupported features

* Defaults aren't supported in Java so [uniffi struct, method, and function defaults](https://mozilla.github.io/uniffi-rs/proc_macro/index.html#default-values) don't exist in the Java code. *Note*: a reasonable case could be made for supporting defaults on structs by way of generated builder patterns. PRs welcome.
* Output formatting isn't currently supported because a standalone command line Java formatter wasn't found. PRs welcome enabling that feature, the infrastructure is in place.

## Testing

We pull down the pinned examples directly from Uniffi (currently v0.31.0) and run Java tests using the generated bindings. Run `cargo t` to run all of them.

Note that if you need additional toml entries for your test, you can put a `uniffi-extras.toml` as a sibling of the test and it will be read in addition to the base `uniffi.toml` for the example. See [CustomTypes](./tests/scripts/TestCustomTypes/) for an example. Settings in `uniffi-extras.toml` apply across all namespaces.

## Versioning

`uniffi-bindgen-java` is versioned separately from `uniffi-rs`. We follow the [Cargo SemVer rules](https://doc.rust-lang.org/cargo/reference/resolver.html#semver-compatibility), so versions are compatible if their left-most non-zero major/minor/patch component is the same. Any modification to the generator that causes a consumer of the generated code to need to make changes is considered breaking.

`uniffi-bindgen-java` is currently unstable and being developed by IronCore Labs to target features required by [`ironcore-alloy`](https://github.com/IronCoreLabs/ironcore-alloy/). The major version is currently 0, and most changes are likely to bump the minor version.

### Compatibility

Keeping this testable requires fully pinned `uniffi-rs` versions. The version of `uniffi-rs` will always be called out in the changelog when it changes, so if you're stuck on a specific version due to other bindings, you can stay on a compatible version of these bindings.
