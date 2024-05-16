use anyhow::{bail, Result};
use camino::{Utf8Path, Utf8PathBuf};
use clap::{Parser, Subcommand};
use std::fs::{self};
use uniffi_bindgen::{BindingGenerator, ComponentInterface};

mod gen_java;

pub struct JavaBindingGenerator;
impl BindingGenerator for JavaBindingGenerator {
    type Config = gen_java::Config;

    fn new_config(&self, root_toml: &toml::Value) -> Result<Self::Config> {
        Ok(
            match root_toml.get("bindings").and_then(|b| b.get("java")) {
                Some(v) => v.clone().try_into()?,
                None => Default::default(),
            },
        )
    }

    fn write_bindings(
        &self,
        ci: &ComponentInterface,
        config: &Self::Config,
        out_dir: &Utf8Path,
        try_format_code: bool, // TODO(murph): not many CLI formatters for Java, may run `java` with expected JAR
    ) -> anyhow::Result<()> {
        let filename_capture = regex::Regex::new(
            r"(?m)^(?:public\s)?(?:final\s)?(?:sealed\s)?(?:abstract\s)?(?:static\s)?(?:class|interface|enum)\s(\w+)",
        )
        .unwrap();
        let bindings_str = gen_java::generate_bindings(&config, &ci)?;
        let java_package_out_dir = out_dir.join(
            config
                .package_name()
                .split(".")
                .collect::<Vec<_>>()
                .join("/"),
        );
        fs::create_dir_all(&java_package_out_dir)?;
        let package_line = format!("package {};", config.package_name());
        let split_classes = bindings_str.split(&package_line);
        let writable = split_classes
            .map(|file| (filename_capture.captures(file), file))
            .filter(|(x, _)| x.is_some())
            .map(|(captures, file)| (captures.unwrap().get(1).unwrap().as_str(), file))
            .collect::<Vec<_>>();
        for (filename, file) in writable {
            fs::write(
                format!("{}/{}.java", java_package_out_dir, filename),
                format!("{}\n{}", package_line, file),
            )?;
        }
        Ok(())
    }

    fn check_library_path(&self, library_path: &Utf8Path, cdylib_name: Option<&str>) -> Result<()> {
        if cdylib_name.is_none() {
            bail!("Generate bindings for Java requires a cdylib, but {library_path} was given");
        }
        Ok(())
    }
}

// TODO(murph): --help and -h still aren't working
/// Scaffolding and bindings generator for Rust
#[derive(Parser)]
#[clap(name = "uniffi-bindgen-java")]
#[clap(version = clap::crate_version!())]
#[clap(propagate_version = true)]
#[command(version, about, long_about = None)]
struct Cli {
    #[clap(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Generate Java bindings
    Generate {
        /// Directory in which to write generated files. Default is same folder as .udl file.
        #[clap(long, short)]
        out_dir: Option<Utf8PathBuf>,

        /// Do not try to format the generated bindings.
        #[clap(long, short)]
        no_format: bool,

        /// Path to optional uniffi config file. This config is merged with the `uniffi.toml` config present in each crate, with its values taking precedence.
        #[clap(long, short)]
        config: Option<Utf8PathBuf>,

        /// Extract proc-macro metadata from a native lib (cdylib or staticlib) for this crate.
        #[clap(long)]
        lib_file: Option<Utf8PathBuf>,

        /// Pass in a cdylib path rather than a UDL file
        #[clap(long = "library")]
        library_mode: bool,

        /// When `--library` is passed, only generate bindings for one crate.
        /// When `--library` is not passed, use this as the crate name instead of attempting to
        /// locate and parse Cargo.toml.
        #[clap(long = "crate")]
        crate_name: Option<String>,

        /// Path to the UDL file, or cdylib if `library-mode` is specified
        source: Utf8PathBuf,
    },
    /// Generate Rust scaffolding code
    Scaffolding {
        /// Directory in which to write generated files. Default is same folder as .udl file.
        #[clap(long, short)]
        out_dir: Option<Utf8PathBuf>,

        /// Do not try to format the generated bindings.
        #[clap(long, short)]
        no_format: bool,

        /// Path to the UDL file.
        udl_file: Utf8PathBuf,
    },
    /// Print a debug representation of the interface from a dynamic library
    PrintRepr {
        /// Path to the library file (.so, .dll, .dylib, or .a)
        path: Utf8PathBuf,
    },
}

pub fn run_main() -> Result<()> {
    let cli = Cli::parse();
    match cli.command {
        Commands::Generate {
            out_dir,
            no_format,
            config,
            lib_file,
            library_mode,
            crate_name,
            source,
        } => {
            if library_mode {
                use uniffi_bindgen::library_mode::generate_bindings;
                if lib_file.is_some() {
                    panic!("--lib-file is not compatible with --library.")
                }
                let out_dir = out_dir.expect("--out-dir is required when using --library");
                generate_bindings(
                    &source,
                    crate_name,
                    &JavaBindingGenerator,
                    config.as_deref(),
                    &out_dir,
                    !no_format,
                )?;
            } else {
                use uniffi_bindgen::generate_bindings;
                generate_bindings(
                    &source,
                    config.as_deref(),
                    JavaBindingGenerator,
                    out_dir.as_deref(),
                    lib_file.as_deref(),
                    crate_name.as_deref(),
                    !no_format,
                )?;
            }
        }
        Commands::Scaffolding {
            out_dir,
            no_format,
            udl_file,
        } => {
            uniffi_bindgen::generate_component_scaffolding(
                &udl_file,
                out_dir.as_deref(),
                !no_format,
            )?;
        }
        Commands::PrintRepr { path } => {
            uniffi_bindgen::print_repr(&path)?;
        }
    };
    Ok(())
}
