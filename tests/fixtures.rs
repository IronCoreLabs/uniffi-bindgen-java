/* This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use anyhow::{bail, Context, Result};
use camino::{Utf8Path, Utf8PathBuf};
use std::env;
use std::process::Command;
use uniffi_testing::UniFFITestHelper;

/// Run the test fixtures from UniFFI

fn run_test(fixture_name: &str, test_file: &str) -> Result<()> {
    let test_path = Utf8Path::new(".").join("tests").join(test_file);
    let test_helper = UniFFITestHelper::new(fixture_name)?;
    let out_dir = test_helper.create_out_dir(env!("CARGO_TARGET_TMPDIR"), test_path)?;
    test_helper.copy_cdylib_to_out_dir(&out_dir)?;
    generate_sources(&out_dir, &test_helper)?;
    let jar_file = build_jar(&fixture_name, &out_dir)?;

    let status = Command::new("javac")
        .arg("-classpath")
        .arg(calc_classpath(vec![&out_dir, &jar_file]))
        // Enable runtime assertions, for easy testing etc.
        .arg("-J-ea")
        // Our tests should not produce any warnings.
        .arg("-Werror")
        .arg(test_path)
        .spawn()
        .context("Failed to spawn `javac` to compile Java test")?
        .wait()
        .context("Failed to wait for `javac` when compiling Java test")?;
    if !status.success() {
        anyhow::bail!("running `javac` failed")
    }
    let compiled_path = test_path.file_stem().unwrap();
    let run_status = Command::new("java")
        .arg("-classpath")
        .arg(calc_classpath(vec![&out_dir, &jar_file]))
        .arg("-Werror")
        .arg(compiled_path)
        .spawn()
        .context("Failed to spawn `java` to run Java test")?
        .wait()
        .context("Failed to wait for `java` when running Java test")?;
    Ok(())
}

fn generate_sources(out_dir: &Utf8PathBuf, test_helper: &UniFFITestHelper) -> Result<()> {
    for source in test_helper.get_compile_sources()? {
        let mut cmd_line = vec![
            "uniffi-bindgen-java".to_string(),
            "--out-dir".to_string(),
            out_dir.into_string(),
        ];
        if let Some(path) = source.config_path {
            cmd_line.push("--config-path".to_string());
            cmd_line.push(path.to_string_lossy().to_string())
        }
        cmd_line.push(source.udl_path.to_string_lossy().to_string());
        println!("{:?}", cmd_line);
        uniffi_bindgen_java::run(cmd_line)?;
    }
    Ok(())
}

/// Generate java bindings for the given namespace, then use the Java
/// command-line tools to compile them into a .jar file.
fn build_jar(fixture_name: &str, out_dir: &Utf8PathBuf) -> Result<Utf8PathBuf> {
    let mut jar_file = Utf8PathBuf::from(out_dir);
    jar_file.push(format!("{}.jar", fixture_name));

    let status = Command::new("javac")
        // Our generated bindings should not produce any warnings; fail tests if they do.
        .arg("-Werror")
        .arg("-d")
        .arg(&jar_file)
        .arg("-classpath")
        // TODO(murph): probably need JNA here
        .arg(calc_classpath(vec![]))
        .args(
            glob::glob(&out_dir.join("*.java").into_string())?
                .flatten()
                .map(|p| String::from(p.to_string_lossy())),
        )
        .spawn()
        .context("Failed to spawn `javac` to compile the bindings")?
        .wait()
        .context("Failed to wait for `javac` when compiling the bindings")?;
    if !status.success() {
        bail!("running `javac` failed")
    }

    let jar_status = Command::new("jar")
        .arg("cf")
        .arg(jar_file.file_name().unwrap())
        .args(
            glob::glob(&out_dir.join("*.class").into_string())?
                .flatten()
                .map(|p| String::from(p.to_string_lossy())),
        )
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
    (test_arithmetic, "uniffi-example-arithmetic", "scripts/test_arithmetic.java"),
    // (test_geometry, "uniffi-example-geometry", "scripts/test_geometry.java"),
    // (test_rondpoint, "uniffi-example-rondpoint", "scripts/test_rondpoint.java"),
    // (test_todolist, "uniffi-example-todolist", "scripts/test_todolist.java"),
    // (test_sprites, "uniffi-example-sprites", "scripts/test_sprites.java"),
    // (test_coverall, "uniffi-fixture-coverall", "scripts/test_coverall.java"),
    // (test_chronological, "uniffi-fixture-time", "scripts/test_chronological.java"),
    // (test_custom_types, "uniffi-example-custom-types", "scripts/test_custom_types.java"),
    // (test_callbacks, "uniffi-fixture-callbacks", "scripts/test_callbacks.java"),
    // (test_external_types, "uniffi-fixture-ext-types", "scripts/test_imported_types.java"),
}
