# uniffi-bindgen-java

Generate [UniFFI](https://github.com/mozilla/uniffi-rs) bindings for Java.

Official Kotlin bindings already exist, which can be used by any JVM language including Java. The Java specific bindings use Java-native types where possible for a more ergonomic interface, for example the Java bindings use `CompletableFutures` instead of `kotlinx.coroutines`.

We highly reccommend you use [UniFFI's proc-macro definition](https://mozilla.github.io/uniffi-rs/latest/proc_macro/index.html) instead of UDL where possible. 

## Requirements

* Java 20+: `javac`, and `jar`
* The [Java Native Access](https://github.com/java-native-access/jna#download) JAR downloaded and its path added to your `$CLASSPATH` environment variable.

## Installation

MSRV is `1.77.0`.

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
  <SOURCE>  Path to the UDL file, or cdylib if `library-mode` is specified

Options:
  -o, --out-dir <OUT_DIR>    Directory in which to write generated files. Default is same folder as .udl file
  -n, --no-format            Do not try to format the generated bindings
  -c, --config <CONFIG>      Path to optional uniffi config file. This config is merged with the `uniffi.toml` config present in each crate, with its values taking precedence
  --lib-file <LIB_FILE>      Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate
  --library                  Pass in a cdylib path rather than a UDL file
  --crate <CRATE_NAME>       When `--library` is passed, only generate bindings for one crate. When `--library` is not passed, use this as the crate name instead of attempting to locate and parse Cargo.toml
```

As an example:

```
> git clone https://github.com/mozilla/uniffi-rs.git
> cd uniffi-rs/examples/arithmetic-proc-macro
> cargo b --release
> uniffi-bindgen-java generate --out-dir ./generated-java --library ../../target/release/libarithmeticpm.so
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
  public static Long add(Long a, Long b) throws ArithmeticException {
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

After generation you'll have an `--out-dir` full of Java files. Package those into a `.jar` using your build tools of choice, and the result can be imported and used as per normal in any Java project with the `JNA` dependency available.

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
| `android` | `false` | Used to toggle on Android specific optimizations (warning: not well tested yet) |
| `android_cleaner` | `android` | Use the `android.system.SystemCleaner` instead of `java.lang.ref.Cleaner`. Fallback in both instances is the one shipped with JNA. |

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
into_custom = "new URI({}).toURL()"
from_custom = "{}.toString()"
```

#### External Types

```
[bindings.java.external_packages]
# This specifies that external types from the crate `rust-crate-name` will be referred by by the package `"java.package.name`.
rust-crate-name = "java.package.name"
```

## Notes

- failures in CompletableFutures will cause them to `completeExceptionally`. The error that caused the failure can be checked with `e.getCause()`. When implementing an async Rust trait in Java, you'll need to `completeExceptionally` instead of throwing. See `TestFixtureFutures.java` for an example trait implementation with errors.
- all primitives are signed in Java by default. Rust correctly interprets the a signed primitive value from Java as unsigned when told to. Callers of Uniffi functions need to be aware when making comparisons (`compareUnsigned`) or printing when a value is actually unsigned to code around footguns on this side.

## Unsupported features

* Defaults aren't supported in Java so [uniffi struct, method, and function defaults](https://mozilla.github.io/uniffi-rs/proc_macro/index.html#default-values) don't exist in the Java code. *Note*: a reasonable case could be made for supporting defaults on structs by way of generated builder patterns. PRs welcome.
* Output formatting isn't currently supported because a standalone command line Java formatter wasn't found. PRs welcome enabling that feature, the infrastructure is in place.

## Testing

We pull down the pinned examples directly from Uniffi and run Java tests using the generated bindings. Run `cargo t` to run all of them.

Note that if you need additional toml entries for your test, you can put a `uniffi-extras.toml` as a sibling of the test and it will be read in addition to the base `uniffi.toml` for the example. See [CustomTypes](./tests/scripts/TestCustomTypes/) for an example. Settings in `uniffi-extras.toml` apply across all namespaces.

## Versioning

`uniffi-bindgen-java` is versioned separately from `uniffi-rs`. We follow the [Cargo SemVer rules](https://doc.rust-lang.org/cargo/reference/resolver.html#semver-compatibility), so versions are compatible if their left-most non-zero major/minor/patch component is the same. Any modification to the generator that causes either a consumer of the generated code to need to make changes is considered breaking.

`uniffi-bindgen-java` is currently unstable and being developed by IronCore Labs to target features required by [`ironcore-alloy`](https://github.com/IronCoreLabs/ironcore-alloy/). The major version is currently 0, and most changes are likely to bump the minor version. 
