/* This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use anyhow::{bail, Context, Result};
use camino::{Utf8Path, Utf8PathBuf};
use cargo_metadata::{Message, MetadataCommand, Package, Target};
use std::env::consts::{ARCH, DLL_EXTENSION};
use std::process::{Command, Stdio};
use std::{env, fs};
use uniffi_bindgen::library_mode::generate_bindings;
use uniffi_bindgen_java::JavaBindingGenerator;
use uniffi_testing::UniFFITestHelper;

/// Run the test fixtures from UniFFI
fn run_test(fixture_name: &str, test_file: &str) -> Result<()> {
    let test_path = Utf8Path::new(".").join("tests").join(test_file);
    let test_helper = UniFFITestHelper::new(fixture_name)?;
    let out_dir = test_helper.create_out_dir(env!("CARGO_TARGET_TMPDIR"), &test_path)?;
    let cdylib_path = find_cdylib_path(fixture_name)?;

    // generate the fixture bindings
    generate_bindings(
        &cdylib_path,
        None,
        &JavaBindingGenerator,
        None,
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
        .arg(dbg!(compiled_path))
        .spawn()
        .context("Failed to spawn `java` to run Java test")?
        .wait()
        .context("Failed to wait for `java` when running Java test")?;
    if !run_status.success() {
        anyhow::bail!("Running the `java` test failed.")
    }

    Ok(())
}

// REPRODUCTION of UniFFITestHelper::find_cdylib_path because it runs `cargo build` of this project
// to try to get the fixture cdylib, but that doesn't build dev dependencies
fn find_cdylib_path(name: &str) -> Result<Utf8PathBuf> {
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
    let mut child = Command::new(env!("CARGO"))
        .arg("test")
        .arg("--no-run")
        .arg("--message-format=json")
        .stdout(Stdio::piped())
        .spawn()
        .expect("Error running cargo build");
    let output = std::io::BufReader::new(child.stdout.take().unwrap());
    let artifacts = Message::parse_stream(output)
        .map(|m| m.expect("Error parsing cargo build messages"))
        .filter_map(|message| match message {
            Message::CompilerArtifact(artifact) => {
                if artifact.target.clone() == *target {
                    Some(artifact.clone())
                } else {
                    None
                }
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    let cdylib_files: Vec<Utf8PathBuf> = artifacts
        .into_iter()
        .flat_map(|artifact| {
            artifact
                .filenames
                .into_iter()
                .filter(|nm| matches!(nm.extension(), Some(DLL_EXTENSION)))
                .collect::<Vec<Utf8PathBuf>>()
        })
        .collect();

    match cdylib_files.len() {
        1 => Ok(cdylib_files[0].to_owned()),
        n => bail!("Found {n} cdylib files for {}", package.name),
    }
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
    // (test_coverall, "uniffi-fixture-coverall", "scripts/test_coverall.java"),
    // (test_chronological, "uniffi-fixture-time", "scripts/test_chronological.java"),
    // (test_custom_types, "uniffi-example-custom-types", "scripts/test_custom_types.java"),
    // (test_callbacks, "uniffi-fixture-callbacks", "scripts/test_callbacks.java"),
    // (test_external_types, "uniffi-fixture-ext-types", "scripts/test_imported_types.java"),
}
