use anyhow::{bail, Context, Result};
use camino::Utf8Path;
use clap::{ArgAction, Command};
use std::fs::{self, File};
use std::io::Write;
use uniffi_bindgen::{generate_external_bindings, BindingGenerator, ComponentInterface};

mod gen_java;

struct JavaBindingGenerator {
    stdout: bool,
}

impl JavaBindingGenerator {
    fn new(stdout: bool) -> Self {
        Self { stdout }
    }

    fn create_writer(
        &self,
        ci: &ComponentInterface,
        out_dir: &Utf8Path,
    ) -> anyhow::Result<Box<dyn Write>> {
        if self.stdout {
            Ok(Box::new(std::io::stdout()))
        } else {
            let filename = format!("{}.java", ci.namespace());
            let out_path = out_dir.join(&filename);
            Ok(Box::new(
                File::create(&out_path).context(format!("Failed to create {:?}", filename))?,
            ))
        }
    }
}

impl BindingGenerator for JavaBindingGenerator {
    type Config = gen_java::Config;

    fn write_bindings(
        &self,
        ci: &ComponentInterface,
        config: &Self::Config,
        out_dir: &Utf8Path,
        try_format_code: bool, // TODO(murph): see what other langs are doing for this
    ) -> anyhow::Result<()> {
        dbg!(config);
        let filename_capture =
            regex::Regex::new(r"(?m)^(?:public\s)?(?:final\s)?(?:class|interface|enum)\s(\w+)")
                .unwrap();
        let bindings_str = gen_java::generate_bindings(&config, &ci)?;
        let package_line = format!("package {};", config.package_name());
        let split_classes = bindings_str.split(&package_line);
        let writable = split_classes
            .map(|file| (filename_capture.captures(file), file))
            .filter(|(x, _)| x.is_some())
            .map(|(captures, file)| (captures.unwrap().get(1).unwrap().as_str(), file))
            .collect::<Vec<_>>();
        for (filename, file) in writable {
            fs::write(
                format!("{}/{}.java", out_dir, filename),
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

pub fn run<I, T>(args: I) -> Result<()>
where
    I: IntoIterator<Item = T>,
    T: Into<std::ffi::OsString> + Clone,
{
    // TODO(murph): --help/-h isn't working
    let matches = Command::new("uniffi-bindgen-java")
        .about("Scaffolding and bindings generator for Rust")
        .version(clap::crate_version!())
        .arg(
            clap::Arg::new("stdout")
                .long("stdout")
                .action(ArgAction::SetTrue)
                .help("Write output to STDOUT"),
        )
        .arg(
            clap::Arg::new("out_dir")
                .long("out-dir")
                .short('o')
                .num_args(1)
                .help("Directory in which to write generated files. Default is same folder as .udl file."),
        )
        .arg(clap::Arg::new("udl_file").required(true))
        .arg(
            clap::Arg::new("config")
            .long("config-path")
            .num_args(1)
            .help("Path to the optional uniffi config file. If not provided, uniffi-bindgen will try to guess it from the UDL's file location.")
        )
        .arg(clap::Arg::new("library_file")
            .long("library-file")
            .num_args(1)
            .help("The path to a dynamic library to attempt to extract the definitions from and extend the component interface with. No extensions to component interface occur if it's [`None`]"))
        .arg(clap::Arg::new("crate_name")
            .long("crate-name")
            .num_args(1)
            .help("Override the default crate name that is guessed from UDL file path."))
        // TODO(murph): not sure this is right
        .arg(clap::Arg::new("try_format_bool")
            .long("try-format")
            .action(ArgAction::SetTrue)
            .help("Try to format the resulting code."))
        .get_matches_from(args);

    let crate_name = matches.get_one::<String>("crate_name");
    let binding_generator = JavaBindingGenerator::new(matches.get_flag("stdout"));
    generate_external_bindings(
        &binding_generator,
        matches.get_one::<String>("udl_file").unwrap(), // Required
        matches.get_one::<String>("config"),
        matches.get_one::<String>("out_dir"),
        matches.get_one::<String>("library_file"),
        crate_name.map(|x| x.as_str()),
        matches.get_flag("try_format_bool"),
    )
}

pub fn run_main() -> Result<()> {
    run(std::env::args_os())
}
