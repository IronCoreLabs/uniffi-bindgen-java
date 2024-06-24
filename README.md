# uniffi-bindgen-java

## Requirements

* Java 20+: `javac`, and `jar`
* The [Java Native Access](https://github.com/java-native-access/jna#download) JAR downloaded and its path added to your `$CLASSPATH` environment variable.

## Unsupported features

* Defaults aren't supported in Java so [uniffi struct, method, and function defaults](https://mozilla.github.io/uniffi-rs/proc_macro/index.html#default-values) don't exist in the Java code. *Note*: a reasonable case could be made for supporting defaults on structs by way of generated builder patterns. PRs welcome.

## Testing

We pull down the pinned examples directly from Uniffi and run Java tests using the generated bindings. Just run `cargo t` to run all of them.

Note that if you need additional toml entries for your test, you can put a `uniffi-extras.toml` as a sibling of the test and it will be read in addition to the base `uniffi.toml` for the example. See [CustomTypes](./tests/scripts/TestCustomTypes/) for an example.

## TODO

- optimize when primitive and boxed types are used. Boxed types are needed when referencing builtins as generics, but we could be using primitives in a lot more function arguments, return types, and value definitions.
