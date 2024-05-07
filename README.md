Currently trying to get a steel thread of anything functioning. Planning to use Askama to generate a single file from any given Rust object, then in the `mod.rs` writing process, split on the package name line and take filenames from the first class/interface per section.

`../../../uniffi-bindgen-java/target/debug/uniffi-bindgen-java generate --out-dir ./out --config uniffi.toml src/arithmetic.udl`
`javac -classpath ~/Downloads/jna-5.14.0.jar -s out ./out/*.java`
