# uniffi-bindgen-java

## Requirements

* Java 20+: `javac`, and `jar`
* The [Java Native Access](https://github.com/java-native-access/jna#download) JAR downloaded and its path added to your `$CLASSPATH` environment variable.

## Unsupported features

* Defaults aren't supported in Java so [uniffi struct, method, and function defaults](https://mozilla.github.io/uniffi-rs/proc_macro/index.html#default-values) don't exist in the Java code. *Note*: a reasonable case could be made for supporting defaults on structs by way of generated builder patterns. PRs welcome.

## Testing

We pull down the pinned examples directly from Uniffi and run Java tests using the generated bindings. Just run `cargo t` to run all of them.
