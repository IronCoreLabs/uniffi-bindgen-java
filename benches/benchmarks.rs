/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

//! JMH benchmark harness for uniffi-bindgen-java
//!
//! This builds the uniffi-fixture-benchmarks cdylib, generates Java bindings,
//! copies them into the Gradle benchmark project, and runs JMH.
//!
//! Usage:
//!   cargo bench
//!   cargo bench --bench benchmarks -- --jmh-args="-f 1 -wi 2 -i 3"  # pass args to JMH

use anyhow::{Context, Result, bail};
use camino::Utf8PathBuf;
use std::env;
use std::env::consts::ARCH;
use std::fs;
use std::path::PathBuf;
use std::process::Command;
use uniffi_bindgen::{BindgenLoader, BindgenPaths};
use uniffi_bindgen_java::{GenerateOptions, generate};
use uniffi_testing::UniFFITestHelper;

fn main() -> Result<()> {
    let jmh_args = parse_jmh_args();
    let project_root = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let bench_project = project_root.join("benchmarks");
    let generated_dir = bench_project.join("build/generated-sources/uniffi");

    // Clean and recreate the generated sources directory
    if generated_dir.exists() {
        fs::remove_dir_all(&generated_dir)?;
    }
    fs::create_dir_all(&generated_dir)?;

    // Build the benchmarks fixture cdylib
    println!("Building benchmarks fixture...");
    let test_helper = UniFFITestHelper::new("uniffi-fixture-benchmarks")?;
    let cdylib_path = test_helper.cdylib_path()?;
    println!("  cdylib: {cdylib_path}");

    // Generate Java bindings
    println!("Generating Java bindings...");
    let out_dir = Utf8PathBuf::from(generated_dir.to_string_lossy().to_string());

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

    // Copy the native library into JNA's expected resource path so it gets packaged
    // into the JMH jar. JNA looks for native libs in {os}-{arch}/ inside the classpath.
    let jna_resource_folder = if cdylib_path.extension().unwrap() == "dylib" {
        format!("darwin-{}", ARCH).replace('_', "-")
    } else {
        format!("linux-{}", ARCH).replace('_', "-")
    };
    let native_resource_dir = bench_project
        .join("build/native-resources")
        .join(&jna_resource_folder);
    fs::create_dir_all(&native_resource_dir)?;
    let cdylib_dest = native_resource_dir.join(cdylib_path.file_name().unwrap());
    fs::copy(cdylib_path.as_std_path(), &cdylib_dest)?;
    println!("  native lib: {}", cdylib_dest.display());

    // Run JMH via Gradle
    println!("Running JMH benchmarks...");
    let mut cmd = Command::new("gradle");
    cmd.current_dir(&bench_project)
        .arg("jmh")
        .arg("--no-daemon")
        .arg("--console=plain")
        .env(
            "UNIFFI_JNA_CLASSPATH",
            env::var("CLASSPATH").unwrap_or_default(),
        );

    // Pass JMH args via project property
    if !jmh_args.is_empty() {
        let args_str = jmh_args.join(" ");
        cmd.arg(format!("-PjmhArgs={args_str}"));
    }

    let status = cmd
        .spawn()
        .context("Failed to spawn gradle. Is gradle available in your PATH (nix develop)?")?
        .wait()
        .context("Failed to wait for gradle")?;

    if !status.success() {
        bail!("JMH benchmark run failed");
    }

    println!("\nResults written to: benchmarks/build/results/jmh/");
    Ok(())
}

fn parse_jmh_args() -> Vec<String> {
    env::args()
        .skip_while(|a| a != "--")
        .skip(1)
        .flat_map(|a| {
            if let Some(args) = a.strip_prefix("--jmh-args=") {
                args.split_whitespace()
                    .map(String::from)
                    .collect::<Vec<_>>()
            } else {
                vec![a]
            }
        })
        .collect()
}
