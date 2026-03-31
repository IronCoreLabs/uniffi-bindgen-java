/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! Criterion benchmark harness for uniffi-bindgen-java
//!
//! Matches the upstream uniffi-rs benchmark style: builds the fixture cdylib,
//! generates Java bindings, compiles a Java benchmark runner, and executes it.
//! Criterion runs inside the Rust fixture library, driven by `runBenchmarks()`.
//!
//! Usage:
//!   cargo bench
//!   cargo bench -- --filter call-only    # filter benchmarks
//!   cargo bench -- --save-baseline name  # save Criterion baseline

use anyhow::{Context, Result, bail};
use camino::Utf8PathBuf;
use std::env;
use std::fs;
use std::path::PathBuf;
use std::process::Command;
use uniffi_bindgen::{BindgenLoader, BindgenPaths};
use uniffi_bindgen_java::{GenerateOptions, generate};
use uniffi_testing::UniFFITestHelper;

fn main() -> Result<()> {
    let project_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let tmp_dir = PathBuf::from(std::env!("CARGO_TARGET_TMPDIR")).join("benchmarks");

    // Clean and recreate the temp directory
    if tmp_dir.exists() {
        fs::remove_dir_all(&tmp_dir)?;
    }
    fs::create_dir_all(&tmp_dir)?;

    // Build the benchmarks fixture cdylib
    println!("Building benchmarks fixture...");
    let test_helper = UniFFITestHelper::new("uniffi-fixture-benchmarks")?;
    let cdylib_path = test_helper.cdylib_path()?;
    println!("  cdylib: {cdylib_path}");

    // Generate Java bindings
    println!("Generating Java bindings...");
    let out_dir = Utf8PathBuf::from(tmp_dir.to_string_lossy().to_string());

    let mut paths = BindgenPaths::default();
    paths.add_cargo_metadata_layer(false)?;
    let loader = BindgenLoader::new(paths);

    generate(
        &loader,
        &GenerateOptions {
            source: cdylib_path.clone(),
            out_dir: out_dir.clone(),
            format: false,
            crate_filter: None,
        },
    )?;

    // Copy cdylib and create symlink for System.loadLibrary
    let native_lib_dir = tmp_dir.join("native");
    fs::create_dir_all(&native_lib_dir)?;
    let cdylib_filename = cdylib_path.file_name().unwrap();
    let cdylib_dest = native_lib_dir.join(cdylib_filename);
    fs::copy(cdylib_path.as_std_path(), &cdylib_dest)?;

    let extension = cdylib_path.extension().unwrap();
    let lib_base_name = cdylib_filename
        .strip_prefix("lib")
        .unwrap_or(cdylib_filename)
        .split('-')
        .next()
        .unwrap_or(cdylib_filename);
    let expected_lib_name = format!("lib{}.{}", lib_base_name, extension);
    let symlink_path = native_lib_dir.join(&expected_lib_name);
    if !symlink_path.exists() {
        std::os::unix::fs::symlink(cdylib_dest.file_name().unwrap(), &symlink_path)?;
    }

    // Compile generated bindings into a jar
    println!("Compiling Java bindings...");
    let staging_dir = tmp_dir.join("staging");
    fs::create_dir_all(&staging_dir)?;

    let java_sources: Vec<_> = glob::glob(&format!("{}/**/*.java", out_dir))?
        .flatten()
        .map(|p| p.to_string_lossy().to_string())
        .collect();

    let classpath = calc_classpath(vec![]);
    let status = Command::new("javac")
        .arg("-d")
        .arg(&staging_dir)
        .arg("-classpath")
        .arg(&classpath)
        .args(&java_sources)
        .spawn()
        .context("Failed to spawn `javac` to compile the bindings")?
        .wait()
        .context("Failed to wait for `javac`")?;
    if !status.success() {
        bail!("javac failed when compiling the generated bindings")
    }

    let jar_file = tmp_dir.join("benchmarks.jar");
    let jar_status = Command::new("jar")
        .current_dir(&tmp_dir)
        .arg("cf")
        .arg(jar_file.file_name().unwrap())
        .arg("-C")
        .arg(&staging_dir)
        .arg(".")
        .spawn()
        .context("Failed to spawn `jar`")?
        .wait()
        .context("Failed to wait for `jar`")?;
    if !jar_status.success() {
        bail!("jar failed when packaging the bindings")
    }

    // Compile the benchmark runner script
    println!("Compiling benchmark runner...");
    let runner_src = project_root.join("benches/bindings/RunBenchmarks.java");
    let runner_classpath = calc_classpath(vec![jar_file.to_string_lossy().to_string()]);
    let status = Command::new("javac")
        .arg("-classpath")
        .arg(&runner_classpath)
        .arg("-d")
        .arg(&tmp_dir)
        .arg(&runner_src)
        .spawn()
        .context("Failed to spawn `javac` to compile the benchmark runner")?
        .wait()
        .context("Failed to wait for `javac`")?;
    if !status.success() {
        bail!("javac failed when compiling the benchmark runner")
    }

    // Run the benchmark
    println!("Running benchmarks...");
    let run_classpath = calc_classpath(vec![
        jar_file.to_string_lossy().to_string(),
        tmp_dir.to_string_lossy().to_string(),
    ]);

    // Collect args after "--" to pass through to the Java process
    let pass_through_args: Vec<String> = env::args().skip_while(|a| a != "--").collect();

    let mut cmd = Command::new("java");
    cmd.arg("-Xmx2g")
        .arg("--enable-native-access=ALL-UNNAMED")
        .arg(format!("-Djava.library.path={}", native_lib_dir.display()))
        .arg("-classpath")
        .arg(&run_classpath)
        .arg("RunBenchmarks")
        .args(&pass_through_args);

    let status = cmd
        .spawn()
        .context("Failed to spawn `java` to run benchmarks")?
        .wait()
        .context("Failed to wait for `java`")?;
    if !status.success() {
        bail!("Benchmark run failed")
    }

    Ok(())
}

fn calc_classpath(extra_paths: Vec<String>) -> String {
    extra_paths
        .into_iter()
        .chain(env::var("CLASSPATH"))
        .collect::<Vec<String>>()
        .join(":")
}
