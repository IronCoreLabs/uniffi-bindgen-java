/* This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use anyhow::{bail, Context, Result};
use camino::{Utf8Path, Utf8PathBuf};
use cargo_metadata::{MetadataCommand, Package, Target};
use std::env::consts::ARCH;
use std::io::{Read, Write};
use std::process::Command;
use std::time::{SystemTime, UNIX_EPOCH};
use std::{env, fs};
use uniffi_bindgen::library_mode::generate_bindings;
use uniffi_bindgen_java::JavaBindingGenerator;
use uniffi_testing::UniFFITestHelper;

/// Run the test fixtures from UniFFI
fn run_test(fixture_name: &str, test_file: &str) -> Result<()> {
    let test_path = Utf8Path::new(".").join("tests").join(test_file);
    let test_helper = UniFFITestHelper::new(fixture_name)?;
    let out_dir = test_helper.create_out_dir(env!("CARGO_TARGET_TMPDIR"), &test_path)?;
    let cdylib_path = test_helper.cdylib_path()?;

    // This whole block in designed to create a new TOML file if there is one in the fixture or a uniffi-extras.toml as a sibling of the test. The extras
    // will be concatenated to the end of the base with extra if available.
    let maybe_new_uniffi_toml_filename = {
        let maybe_base_uniffi_toml_string =
            find_uniffi_toml(fixture_name)?.and_then(read_file_contents);
        let maybe_extra_uniffi_toml_string =
            read_file_contents(test_path.with_file_name("uniffi-extras.toml"));

        // final_string will be "" if there aren't any toml files to read.
        let final_string: String = itertools::Itertools::intersperse(
            vec![
                maybe_base_uniffi_toml_string,
                maybe_extra_uniffi_toml_string,
            ]
            .into_iter()
            .filter_map(|s| s),
            "\n".to_string(),
        )
        .collect();

        // If there wasn't anything read from the files, just return none so the default config file can be used.
        if final_string == "" {
            None
        } else {
            //Create a unique(ish) filename for the fixture. We'll just accept that nanosecond uniqueness is good enough per fixture_name.
            let current_time = SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .expect("Time went backwards")
                .as_nanos();
            let new_filename =
                out_dir.with_file_name(format!("{}-{}.toml", fixture_name, current_time));
            write_file_contents(&new_filename, &final_string)?;
            Some(new_filename)
        }
    };

    let config_supplier = {
        use uniffi_bindgen::cargo_metadata::CrateConfigSupplier;
        let cmd = cargo_metadata::MetadataCommand::new();
        let metadata = cmd.exec()?;
        CrateConfigSupplier::from(metadata)
    };

    // generate the fixture bindings
    generate_bindings(
        &cdylib_path,
        None,
        &JavaBindingGenerator,
        &config_supplier,
        maybe_new_uniffi_toml_filename.as_deref(),
        &out_dir,
        true,
    )?;

    // jna requires a specific resources path inside the jar by default, create that folder
    let cdylib_java_resource_folder = if cdylib_path.extension().unwrap() == "dylib" {
        format!("darwin-{}", ARCH).replace("_", "-")
    } else {
        format!("linux-{}", ARCH).replace("_", "-")
    };
    let cdylib_java_resource_path = out_dir.join("staging").join(cdylib_java_resource_folder);
    fs::create_dir_all(&cdylib_java_resource_path)?;
    let cdylib_dest = cdylib_java_resource_path.join(cdylib_path.file_name().unwrap());
    fs::copy(&cdylib_path, &cdylib_dest)?;

    // compile generated bindings and form jar
    let jar_file = build_jar(&fixture_name, &out_dir)?;

    // compile test
    let status = Command::new("javac")
        .arg("-classpath")
        .arg(calc_classpath(vec![&out_dir, &jar_file]))
        // Our tests should not produce any warnings.
        .arg("-Werror")
        .arg(&test_path)
        .spawn()
        .context("Failed to spawn `javac` to compile Java test")?
        .wait()
        .context("Failed to wait for `javac` when compiling Java test")?;
    if !status.success() {
        anyhow::bail!("running `javac` failed when compiling the Java test")
    }

    // run resulting test
    let compiled_path = test_path.file_stem().unwrap();
    let run_status = Command::new("java")
        // allow for runtime assertions
        .arg("-ea")
        .arg("-classpath")
        .arg(calc_classpath(vec![
            &out_dir,
            &jar_file,
            &test_path.parent().unwrap().to_path_buf(),
        ]))
        .arg(compiled_path)
        .spawn()
        .context("Failed to spawn `java` to run Java test")?
        .wait()
        .context("Failed to wait for `java` when running Java test")?;
    if !run_status.success() {
        anyhow::bail!("Running the `java` test failed.")
    }

    Ok(())
}

/// Get the uniffi_toml of the fixture if it exists.
/// It looks for it in the root directory of the project `name`.
fn find_uniffi_toml(name: &str) -> Result<Option<Utf8PathBuf>> {
    let metadata = MetadataCommand::new()
        .exec()
        .expect("error running cargo metadata");
    let matching: Vec<&Package> = metadata
        .packages
        .iter()
        .filter(|p| p.name == name)
        .collect();
    let package = match matching.len() {
        1 => matching[0].clone(),
        n => bail!("cargo metadata return {n} packages named {name}"),
    };
    let cdylib_targets: Vec<&Target> = package
        .targets
        .iter()
        .filter(|t| t.crate_types.iter().any(|t| t == "cdylib"))
        .collect();
    let target = match cdylib_targets.len() {
        1 => cdylib_targets[0],
        n => bail!("Found {n} cdylib targets for {}", package.name),
    };
    let maybe_uniffi_toml = target
        .src_path
        .parent()
        .map(|uniffi_toml_dir| uniffi_toml_dir.with_file_name("uniffi.toml"));
    Ok(maybe_uniffi_toml)
}

/// Generate java bindings for the given namespace, then use the Java
/// command-line tools to compile them into a .jar file.
fn build_jar(fixture_name: &str, out_dir: &Utf8PathBuf) -> Result<Utf8PathBuf> {
    let mut jar_file = Utf8PathBuf::from(out_dir);
    jar_file.push(format!("{}.jar", fixture_name));
    let staging_dir = out_dir.join("staging");

    let status = Command::new("javac")
        // Our generated bindings should not produce any warnings; fail tests if they do.
        .arg("-Werror")
        .arg("-d")
        .arg(&staging_dir)
        .arg("-classpath")
        // JNA must already be in the system classpath
        .arg(calc_classpath(vec![]))
        .args(
            glob::glob(&out_dir.join("**/*.java").into_string())?
                .flatten()
                .map(|p| String::from(p.to_string_lossy())),
        )
        .spawn()
        .context("Failed to spawn `javac` to compile the bindings")?
        .wait()
        .context("Failed to wait for `javac` when compiling the bindings")?;
    if !status.success() {
        bail!("running `javac` failed when compiling the bindings")
    }

    let jar_status = Command::new("jar")
        .current_dir(out_dir)
        .arg("cf")
        .arg(jar_file.file_name().unwrap())
        .arg("-C")
        .arg(&staging_dir)
        .arg(".")
        .spawn()
        .context("Failed to spawn `jar` to package the bindings")?
        .wait()
        .context("Failed to wait for `jar` when packaging the bindings")?;
    if !jar_status.success() {
        bail!("running `jar` failed")
    }

    Ok(jar_file)
}

fn calc_classpath(extra_paths: Vec<&Utf8PathBuf>) -> String {
    extra_paths
        .into_iter()
        .map(|p| p.to_string())
        // Add the system classpath as a component, using the fact that env::var returns an Option,
        // which implement Iterator
        .chain(env::var("CLASSPATH"))
        .collect::<Vec<String>>()
        .join(":")
}

/// Read the contents of the file. Any errors will be turned into None.
fn read_file_contents(path: Utf8PathBuf) -> Option<String> {
    if let Ok(metadata) = fs::metadata(&path) {
        if metadata.is_file() {
            let mut content = String::new();
            std::fs::File::open(path)
                .ok()?
                .read_to_string(&mut content)
                .ok()?;
            Some(content)
        } else {
            None
        }
    } else {
        None
    }
}

fn write_file_contents(path: &Utf8PathBuf, contents: &str) -> Result<()> {
    std::fs::File::create(path)?.write_all(contents.as_bytes())?;
    Ok(())
}

macro_rules! fixture_tests {
    {
        $(($test_name:ident, $fixture_name:expr, $test_script:expr),)*
    } => {
    $(
        #[test]
        fn $test_name() -> Result<()> {
            run_test($fixture_name, $test_script)
        }
    )*
    }
}

fixture_tests! {
    (test_arithmetic, "uniffi-example-arithmetic", "scripts/TestArithmetic.java"),
    (test_geometry, "uniffi-example-geometry", "scripts/TestGeometry.java"),
    (test_rondpoint, "uniffi-example-rondpoint", "scripts/TestRondpoint.java"),
    // (test_todolist, "uniffi-example-todolist", "scripts/test_todolist.java"),
    // (test_sprites, "uniffi-example-sprites", "scripts/test_sprites.java"),
    (test_coverall, "uniffi-fixture-coverall", "scripts/TestFixtureCoverall.java"),
    (test_chronological, "uniffi-fixture-time", "scripts/TestChronological.java"),
    (test_custom_types, "uniffi-example-custom-types", "scripts/TestCustomTypes/TestCustomTypes.java"),
    // (test_callbacks, "uniffi-fixture-callbacks", "scripts/test_callbacks.java"),
    (test_external_types, "uniffi-fixture-ext-types", "scripts/TestImportedTypes/TestImportedTypes.java"),
    (test_futures, "uniffi-example-futures", "scripts/TestFutures.java"),
    (test_futures_fixtures, "uniffi-fixture-futures", "scripts/TestFixtureFutures/TestFixtureFutures.java"),
    (test_cancel_delay_fixtures, "uniffi-fixture-futures", "scripts/TestFixtureCancelDelay/TestFixtureCancelDelay.java"),
}
