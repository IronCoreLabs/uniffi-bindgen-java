use anyhow::{Context, Result};
use askama::Template;
use core::fmt::Debug;
use heck::{ToLowerCamelCase, ToShoutySnakeCase, ToUpperCamelCase};
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize};
use std::{
    borrow::Borrow,
    cell::RefCell,
    collections::{HashMap, HashSet},
};
use uniffi_bindgen::{interface::*, to_askama_error};

mod callback_interface;
mod compounds;
mod custom;
mod enum_;
mod miscellany;
mod object;
mod primitives;
mod record;
mod variant;

pub fn potentially_add_external_package(
    config: &Config,
    ci: &ComponentInterface,
    type_name: &str,
    display_name: String,
) -> String {
    match ci.get_type(type_name) {
        Some(typ) => {
            if ci.is_external(&typ) {
                format!(
                    "{}.{}",
                    config.external_type_package_name(typ.module_path().unwrap(), &display_name),
                    display_name
                )
            } else {
                display_name
            }
        }
        None => display_name,
    }
}

trait CodeType: Debug {
    /// The language specific label used to reference this type. This will be used in
    /// method signatures and property declarations.
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String;

    /// The primitive type label if this type is a primitive (int, long, boolean, etc.).
    /// Returns None for non-primitive types.
    fn type_label_primitive(&self) -> Option<String> {
        None
    }

    /// A representation of this type label that can be used as part of another
    /// identifier. e.g. `read_foo()`, or `FooInternals`.
    ///
    /// This is especially useful when creating specialized objects or methods to deal
    /// with this type only.
    fn canonical_name(&self) -> String;

    /// Instance of the FfiConverter
    ///
    /// This is the object that contains the lower, write, lift, and read methods for this type.
    /// Depending on the binding this will either be a singleton or a class with static methods.
    ///
    /// This is the newer way of handling these methods and replaces the lower, write, lift, and
    /// read CodeType methods.
    fn ffi_converter_name(&self) -> String {
        format!("FfiConverter{}", self.canonical_name())
    }

    /// Name of the FfiConverter
    ///
    /// This is the object that contains the lower, write, lift, and read methods for this type.
    /// Depending on the binding this will either be a singleton or a class with static methods.
    ///
    /// This is the newer way of handling these methods and replaces the lower, write, lift, and
    /// read CodeType methods.
    fn ffi_converter_instance(&self, _config: &Config, _ci: &ComponentInterface) -> String {
        format!("{}.INSTANCE", self.ffi_converter_name())
    }

    /// Function to run at startup
    fn initialization_fn(&self) -> Option<String> {
        None
    }
}

// taken from https://docs.oracle.com/javase/specs/ section 3.9
static KEYWORDS: Lazy<HashSet<String>> = Lazy::new(|| {
    let kwlist = vec![
        "abstract",
        "continue",
        "for",
        "new",
        "switch",
        "assert",
        "default",
        "if",
        "package",
        "synchronized",
        "boolean",
        "do",
        "goto",
        "private",
        "this",
        "break",
        "double",
        "implements",
        "protected",
        "throw",
        "byte",
        "else",
        "import",
        "public",
        "throws",
        "case",
        "enum",
        "instanceof",
        "return",
        "transient",
        "catch",
        "extends",
        "int",
        "short",
        "try",
        "char",
        "final",
        "interface",
        "static",
        "void",
        "class",
        "finally",
        "long",
        "strictfp",
        "volatile",
        "const",
        "float",
        "native",
        "super",
        "while",
        "_",
    ];
    HashSet::from_iter(kwlist.into_iter().map(|s| s.to_string()))
});

// config options to customize the generated Java.
#[derive(Debug, Default, Clone, Serialize, Deserialize)]
pub struct Config {
    pub(super) package_name: Option<String>,
    pub(super) cdylib_name: Option<String>,
    generate_immutable_records: Option<bool>,
    #[serde(default)]
    omit_checksums: bool,
    #[serde(default)]
    custom_types: HashMap<String, CustomTypeConfig>,
    #[serde(default)]
    pub(super) external_packages: HashMap<String, String>,
    #[serde(default)]
    android: bool,
    #[serde(default)]
    nullness_annotations: bool,
    /// Renames for types, fields, methods, variants, and arguments.
    /// Uses dot notation: "OldRecord" = "NewRecord", "OldRecord.field" = "new_field"
    #[serde(default)]
    pub(super) rename: toml::Table,
}

impl Config {
    pub fn omit_checksums(&self) -> bool {
        self.omit_checksums
    }

    /// Whether to generate PanamaPort imports for Android compatibility.
    /// When true, post-processes generated code to replace `java.lang.foreign.*` with
    /// PanamaPort's `com.v7878.foreign.*` and `java.lang.invoke.VarHandle` with
    /// `com.v7878.invoke.VarHandle`.
    pub fn android(&self) -> bool {
        self.android
    }

    /// Whether to generate JSpecify `@NullMarked` and `@Nullable` annotations.
    pub fn nullness_annotations(&self) -> bool {
        self.nullness_annotations
    }
}

impl Config {
    pub fn package_name(&self) -> String {
        if let Some(package_name) = &self.package_name {
            package_name.clone()
        } else {
            "uniffi".into()
        }
    }

    pub fn cdylib_name(&self) -> String {
        if let Some(cdylib_name) = &self.cdylib_name {
            cdylib_name.clone()
        } else {
            "uniffi".into()
        }
    }

    /// Whether to generate immutable records (`record` instead of `class`)
    pub fn generate_immutable_records(&self) -> bool {
        self.generate_immutable_records.unwrap_or(false)
    }

    // Get the package name for an external type
    pub fn external_type_package_name(&self, module_path: &str, namespace: &str) -> String {
        // config overrides are keyed by the crate name, default fallback is the namespace.
        let crate_name = module_path.split("::").next().unwrap();
        match self.external_packages.get(crate_name) {
            Some(name) => name.clone(),
            // unreachable in library mode - all deps are in our config with correct namespace.
            None => format!("uniffi.{namespace}"),
        }
    }
}

#[derive(Debug, Default, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct CustomTypeConfig {
    imports: Option<Vec<String>>,
    type_name: Option<String>,
    into_custom: String, // backcompat alias for lift
    lift: String,
    from_custom: String, // backcompat alias for lower
    lower: String,
}

// functions replace literal "{}" in strings with a specified value.
impl CustomTypeConfig {
    fn lift(&self, name: &str) -> String {
        let converter = if self.lift.is_empty() {
            &self.into_custom
        } else {
            &self.lift
        };
        converter.replace("{}", name)
    }
    fn lower(&self, name: &str) -> String {
        let converter = if self.lower.is_empty() {
            &self.from_custom
        } else {
            &self.lower
        };
        converter.replace("{}", name)
    }
}

// Generate Java bindings for the given ComponentInterface, as a string.
pub fn generate_bindings(config: &Config, ci: &ComponentInterface) -> Result<String> {
    let output = JavaWrapper::new(config.clone(), ci)
        .render()
        .context("failed to render java bindings")?;

    if config.android() {
        // PanamaPort provides the FFM API under a different package prefix.
        // The API surface is identical so a global string replacement is sufficient.
        // `java.lang.invoke.MethodHandle/MethodHandles/MethodType` are available on
        // Android API 26+ and are intentionally left unchanged.
        Ok(output
            .replace("java.lang.foreign.", "com.v7878.foreign.")
            .replace("java.lang.invoke.VarHandle", "com.v7878.invoke.VarHandle"))
    } else {
        Ok(output)
    }
}

#[derive(Template)]
#[template(syntax = "java", escape = "none", path = "wrapper.java")]
pub struct JavaWrapper<'a> {
    config: Config,
    ci: &'a ComponentInterface,
    type_helper_code: String,
}

impl<'a> JavaWrapper<'a> {
    pub fn new(config: Config, ci: &'a ComponentInterface) -> Self {
        let type_renderer = TypeRenderer::new(&config, ci);
        let type_helper_code = type_renderer.render().unwrap();
        Self {
            config,
            ci,
            type_helper_code,
        }
    }

    pub fn initialization_fns(&self) -> Vec<String> {
        self.ci
            .iter_local_types()
            .map(|t| JavaCodeOracle.find(t))
            .filter_map(|ct| ct.initialization_fn())
            .collect()
    }

    /// Get the namespace class name, avoiding collisions with type names.
    /// If the namespace name (after camelCase conversion) collides with any type,
    /// append "Lib" suffix repeatedly until there's no collision.
    pub fn namespace_class_name(&self) -> String {
        let base_name = JavaCodeOracle.class_name(self.ci, self.ci.namespace());

        // Collect all type names for collision checking
        let type_names: HashSet<String> = self
            .ci
            .iter_local_types()
            .map(|t| JavaCodeOracle.find(t).type_label(self.ci, &self.config))
            .collect();

        // Keep adding "Lib" suffix until there's no collision
        let mut candidate = base_name;
        while type_names.contains(&candidate) {
            candidate = format!("{}Lib", candidate);
        }
        candidate
    }
}

/// Renders Java helper code for all types
///
/// This template is a bit different than others in that it stores internal state from the render
/// process.  Make sure to only call `render()` once.
#[derive(Template)]
#[template(syntax = "java", escape = "none", path = "Types.java")]
pub struct TypeRenderer<'a> {
    config: &'a Config,
    ci: &'a ComponentInterface,
    // Track included modules for the `include_once()` macro
    include_once_names: RefCell<HashSet<String>>,
}

impl<'a> TypeRenderer<'a> {
    fn new(config: &'a Config, ci: &'a ComponentInterface) -> Self {
        Self {
            config,
            ci,
            include_once_names: RefCell::new(HashSet::new()),
        }
    }

    // The following methods are used by the `Types.java` macros.

    // Helper for the including a template, but only once.
    //
    // The first time this is called with a name it will return true, indicating that we should
    // include the template.  Subsequent calls will return false.
    fn include_once_check(&self, name: &str) -> bool {
        self.include_once_names
            .borrow_mut()
            .insert(name.to_string())
    }

    // Get the package name for an external type (used by ExternalTypeTemplate.java)
    fn external_type_package_name(&self, module_path: &str, namespace: &str) -> String {
        self.config
            .external_type_package_name(module_path, namespace)
    }
}

fn fixup_keyword(name: String) -> String {
    if KEYWORDS.contains(&name) {
        format!("_{name}")
    } else {
        name
    }
}

#[derive(Clone)]
pub struct JavaCodeOracle;

impl JavaCodeOracle {
    fn find(&self, type_: &Type) -> Box<dyn CodeType> {
        type_.clone().as_type().as_codetype()
    }

    /// Get the idiomatic Java rendering of a class name (for enums, records, errors, etc).
    fn class_name(&self, ci: &ComponentInterface, nm: &str) -> String {
        let name = nm.to_string().to_upper_camel_case();
        // fixup errors.
        fixup_keyword(if ci.is_name_used_as_error(nm) {
            self.convert_error_suffix(&name)
        } else {
            name
        })
    }

    fn convert_error_suffix(&self, nm: &str) -> String {
        match nm.strip_suffix("Error") {
            Some(stripped) if !stripped.is_empty() => format!("{stripped}Exception"),
            _ => nm.to_string(),
        }
    }

    /// Get the idiomatic Java rendering of a function name.
    fn fn_name(&self, nm: &str) -> String {
        fixup_keyword(nm.to_string().to_lower_camel_case())
    }

    /// Get the idiomatic Java rendering of a variable name.
    pub fn var_name(&self, nm: &str) -> String {
        fixup_keyword(self.var_name_raw(nm))
    }

    /// `var_name` without the reserved word alteration.  Useful for struct field names.
    pub fn var_name_raw(&self, nm: &str) -> String {
        nm.to_string().to_lower_camel_case()
    }

    /// Get the idiomatic setter name for a variable.
    pub fn setter(&self, nm: &str) -> String {
        format!("set{}", fixup_keyword(nm.to_string().to_upper_camel_case()))
    }

    /// Get the idiomatic Java rendering of an individual enum variant.
    fn enum_variant_name(&self, nm: &str) -> String {
        nm.to_string().to_shouty_snake_case()
    }

    /// Get the idiomatic Java rendering of an FFI callback function name
    fn ffi_callback_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    /// Get the idiomatic Java rendering of an FFI struct name
    fn ffi_struct_name(&self, nm: &str) -> String {
        format!("Uniffi{}", nm.to_upper_camel_case())
    }

    /// FFI type label for use in method signatures and MethodHandle wrapper methods.
    /// In FFM, primitives stay as primitives, everything else is MemorySegment
    /// (except Handle/RustArcPtr which are long).
    fn ffi_type_label(
        &self,
        ffi_type: &FfiType,
        _config: &Config,
        _ci: &ComponentInterface,
    ) -> String {
        match ffi_type {
            // Note that unsigned values in Java don't have true native support. Signed primitives
            // can contain unsigned values and there are methods like `Integer.compareUnsigned`
            // that respect the unsigned value, but knowledge outside the type system is required.
            // TODO(java): improve callers knowledge of what contains an unsigned value
            FfiType::Int8 | FfiType::UInt8 => "byte".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "short".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "int".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "long".to_string(),
            FfiType::Float32 => "float".to_string(),
            FfiType::Float64 => "double".to_string(),
            FfiType::Handle => "long".to_string(),
            // In FFM, all struct types, buffers, pointers, and callbacks are MemorySegment
            FfiType::RustBuffer(_)
            | FfiType::RustCallStatus
            | FfiType::ForeignBytes
            | FfiType::Callback(_)
            | FfiType::Struct(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => "java.lang.foreign.MemorySegment".to_string(),
        }
    }

    /// FFI type label using boxed types for use in generic contexts (e.g. FfiConverter<T, Long>).
    /// Java generics don't accept primitives.
    fn ffi_type_label_boxed(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => "java.lang.Byte".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "java.lang.Short".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "java.lang.Integer".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "java.lang.Long".to_string(),
            FfiType::Float32 => "java.lang.Float".to_string(),
            FfiType::Float64 => "java.lang.Double".to_string(),
            FfiType::Handle => "java.lang.Long".to_string(),
            _ => "java.lang.foreign.MemorySegment".to_string(),
        }
    }

    /// Maps FfiType to ValueLayout constant for FunctionDescriptor construction
    fn ffi_value_layout(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => "java.lang.foreign.ValueLayout.JAVA_BYTE".to_string(),
            FfiType::Int16 | FfiType::UInt16 => {
                "java.lang.foreign.ValueLayout.JAVA_SHORT".to_string()
            }
            FfiType::Int32 | FfiType::UInt32 => {
                "java.lang.foreign.ValueLayout.JAVA_INT".to_string()
            }
            FfiType::Int64 | FfiType::UInt64 => {
                "java.lang.foreign.ValueLayout.JAVA_LONG".to_string()
            }
            FfiType::Float32 => "java.lang.foreign.ValueLayout.JAVA_FLOAT".to_string(),
            FfiType::Float64 => "java.lang.foreign.ValueLayout.JAVA_DOUBLE".to_string(),
            FfiType::Handle => "java.lang.foreign.ValueLayout.JAVA_LONG".to_string(),
            FfiType::RustBuffer(_) => "RustBuffer.LAYOUT".to_string(),
            // RustCallStatus is passed as a pointer in the C ABI
            FfiType::RustCallStatus => "java.lang.foreign.ValueLayout.ADDRESS".to_string(),
            FfiType::ForeignBytes => "ForeignBytes.LAYOUT".to_string(),
            FfiType::Struct(name) => format!("{}.LAYOUT", self.ffi_struct_name(name)),
            FfiType::Callback(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => "java.lang.foreign.ValueLayout.ADDRESS".to_string(),
        }
    }

    /// Returns the alignment requirement (in bytes) for an FFI type
    fn ffi_type_alignment(&self, ffi_type: &FfiType) -> usize {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => 1,
            FfiType::Int16 | FfiType::UInt16 => 2,
            FfiType::Int32 | FfiType::UInt32 | FfiType::Float32 => 4,
            FfiType::Int64 | FfiType::UInt64 | FfiType::Float64 | FfiType::Handle => 8,
            // Pointers are pointer-aligned (8 bytes on 64-bit)
            FfiType::Callback(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => 8,
            // Structs: alignment is the max alignment of their fields.
            // RustBuffer has JAVA_LONG (8) as first field → 8-byte aligned
            // RustCallStatus starts with JAVA_BYTE but contains RustBuffer → 8-byte aligned
            // ForeignBytes starts with JAVA_INT → but contains ADDRESS → 8-byte aligned
            FfiType::RustBuffer(_)
            | FfiType::RustCallStatus
            | FfiType::ForeignBytes
            | FfiType::Struct(_) => 8,
        }
    }

    /// Returns the size (in bytes) for an FFI type
    fn ffi_type_size(&self, ffi_type: &FfiType) -> usize {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => 1,
            FfiType::Int16 | FfiType::UInt16 => 2,
            FfiType::Int32 | FfiType::UInt32 | FfiType::Float32 => 4,
            FfiType::Int64 | FfiType::UInt64 | FfiType::Float64 | FfiType::Handle => 8,
            FfiType::Callback(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => 8,
            // Struct sizes: these are hardcoded based on their layouts
            FfiType::RustBuffer(_) => 24,  // long + long + pointer
            FfiType::RustCallStatus => 32, // byte + 7 pad + RustBuffer(24)
            FfiType::ForeignBytes => 16,   // int + 4 pad + pointer
            FfiType::Struct(_) => 0,       // unknown at codegen time, handled by LAYOUT
        }
    }

    /// Maps FfiType to layout for use as a struct field in StructLayout definitions.
    /// Unlike ffi_value_layout (for FunctionDescriptors), embedded structs use their
    /// full LAYOUT here rather than ADDRESS.
    fn ffi_struct_field_layout(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::RustBuffer(_) => "RustBuffer.LAYOUT".to_string(),
            FfiType::RustCallStatus => "UniffiRustCallStatus.LAYOUT".to_string(),
            FfiType::ForeignBytes => "ForeignBytes.LAYOUT".to_string(),
            FfiType::Struct(name) => format!("{}.LAYOUT", self.ffi_struct_name(name)),
            _ => self.ffi_value_layout(ffi_type),
        }
    }

    /// Maps FfiType to UNALIGNED ValueLayout for struct field access
    fn ffi_value_layout_unaligned(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => "java.lang.foreign.ValueLayout.JAVA_BYTE".to_string(), // byte has no alignment
            FfiType::Int16 | FfiType::UInt16 => {
                "java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED".to_string()
            }
            FfiType::Int32 | FfiType::UInt32 => {
                "java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED".to_string()
            }
            FfiType::Int64 | FfiType::UInt64 => {
                "java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED".to_string()
            }
            FfiType::Float32 => "java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED".to_string(),
            FfiType::Float64 => "java.lang.foreign.ValueLayout.JAVA_DOUBLE_UNALIGNED".to_string(),
            FfiType::Handle => "java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED".to_string(),
            FfiType::Callback(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => {
                "java.lang.foreign.ValueLayout.ADDRESS_UNALIGNED".to_string()
            }
            // Structs use slice-based access, not ValueLayout access
            _ => self.ffi_value_layout(ffi_type),
        }
    }

    /// Cast prefix needed for invokeExact() return values
    fn ffi_invoke_exact_cast(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::Int8 | FfiType::UInt8 => "(byte) ".to_string(),
            FfiType::Int16 | FfiType::UInt16 => "(short) ".to_string(),
            FfiType::Int32 | FfiType::UInt32 => "(int) ".to_string(),
            FfiType::Int64 | FfiType::UInt64 => "(long) ".to_string(),
            FfiType::Float32 => "(float) ".to_string(),
            FfiType::Float64 => "(double) ".to_string(),
            FfiType::Handle => "(long) ".to_string(),
            FfiType::RustBuffer(_)
            | FfiType::RustCallStatus
            | FfiType::ForeignBytes
            | FfiType::Callback(_)
            | FfiType::Struct(_)
            | FfiType::VoidPointer
            | FfiType::Reference(_)
            | FfiType::MutReference(_) => "(java.lang.foreign.MemorySegment) ".to_string(),
        }
    }

    /// Returns true if the FFI type is a struct that requires a SegmentAllocator
    /// as the first argument to invokeExact() for downcall handles
    fn ffi_type_is_struct(&self, ffi_type: &FfiType) -> bool {
        // RustCallStatus is passed as a pointer, not by value, so it doesn't need SegmentAllocator
        matches!(
            ffi_type,
            FfiType::RustBuffer(_) | FfiType::ForeignBytes | FfiType::Struct(_)
        )
    }

    /// Returns true if this FFI type is an embedded struct inside another struct
    /// (uses slice-based access rather than primitive get/set)
    fn ffi_type_is_embedded_struct(&self, ffi_type: &FfiType) -> bool {
        matches!(
            ffi_type,
            FfiType::RustBuffer(_)
                | FfiType::RustCallStatus
                | FfiType::ForeignBytes
                | FfiType::Struct(_)
        )
    }

    /// Get the struct class name for an FfiType::Struct
    fn ffi_struct_type_name(&self, ffi_type: &FfiType) -> String {
        match ffi_type {
            FfiType::Struct(name) => self.ffi_struct_name(name),
            FfiType::RustBuffer(_) => "RustBuffer".to_string(),
            FfiType::RustCallStatus => "UniffiRustCallStatus".to_string(),
            FfiType::ForeignBytes => "ForeignBytes".to_string(),
            _ => panic!("ffi_struct_type_name called on non-struct type: {ffi_type:?}"),
        }
    }

    /// Get the name of the interface and class name for an object.
    ///
    /// If we support callback interfaces, the interface name is the object name, and the class name is derived from that.
    /// Otherwise, the class name is the object name and the interface name is derived from that.
    ///
    /// This split determines what types `FfiConverter.lower()` inputs.  If we support callback
    /// interfaces, `lower` must lower anything that implements the interface.  If not, then lower
    /// only lowers the concrete class.
    fn object_names(&self, ci: &ComponentInterface, obj: &Object) -> (String, String) {
        let class_name = self.class_name(ci, obj.name());
        if obj.has_callback_interface() {
            let impl_name = format!("{class_name}Impl");
            (class_name, impl_name)
        } else {
            (format!("{class_name}Interface"), class_name)
        }
    }
}

trait AsCodeType {
    fn as_codetype(&self) -> Box<dyn CodeType>;
}

// Workaround for the possibility of upstream additions of AsType breaking compilation
// Downside to this is new types need to be manually added
impl AsCodeType for Type {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        // Map `Type` instances to a `Box<dyn CodeType>` for that type.
        //
        // There is a companion match in `templates/Types.java` which performs a similar function for the
        // template code.
        //
        //   - When adding additional types here, make sure to also add a match arm to the `Types.java` template.
        //   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
        match self.as_type() {
            Type::UInt8 | Type::Int8 => Box::new(primitives::Int8CodeType),
            Type::UInt16 | Type::Int16 => Box::new(primitives::Int16CodeType),
            Type::UInt32 | Type::Int32 => Box::new(primitives::Int32CodeType),
            Type::UInt64 | Type::Int64 => Box::new(primitives::Int64CodeType),
            Type::Float32 => Box::new(primitives::Float32CodeType),
            Type::Float64 => Box::new(primitives::Float64CodeType),
            Type::Boolean => Box::new(primitives::BooleanCodeType),
            Type::String => Box::new(primitives::StringCodeType),
            Type::Bytes => Box::new(primitives::BytesCodeType),

            Type::Timestamp => Box::new(miscellany::TimestampCodeType),
            Type::Duration => Box::new(miscellany::DurationCodeType),

            Type::Enum { name, .. } => Box::new(enum_::EnumCodeType::new(name.clone())),
            Type::Object { name, imp, .. } => {
                Box::new(object::ObjectCodeType::new(name.clone(), imp))
            }
            Type::Record { name, .. } => Box::new(record::RecordCodeType::new(name.clone())),
            Type::CallbackInterface { name, .. } => Box::new(
                callback_interface::CallbackInterfaceCodeType::new(name.clone()),
            ),
            Type::Optional { inner_type } => {
                Box::new(compounds::OptionalCodeType::new((*inner_type).clone()))
            }
            Type::Sequence { inner_type } => match inner_type.as_ref() {
                Type::Int16 | Type::UInt16 => Box::new(compounds::Int16ArrayCodeType),
                Type::Int32 | Type::UInt32 => Box::new(compounds::Int32ArrayCodeType),
                Type::Int64 | Type::UInt64 => Box::new(compounds::Int64ArrayCodeType),
                Type::Float32 => Box::new(compounds::Float32ArrayCodeType),
                Type::Float64 => Box::new(compounds::Float64ArrayCodeType),
                Type::Boolean => Box::new(compounds::BooleanArrayCodeType),
                // Int8/UInt8 sequences still use SequenceCodeType; the separate Bytes type handles byte[]
                _ => Box::new(compounds::SequenceCodeType::new((*inner_type).clone())),
            },
            Type::Map {
                key_type,
                value_type,
            } => Box::new(compounds::MapCodeType::new(
                (*key_type).clone(),
                (*value_type).clone(),
            )),
            Type::Custom { name, .. } => Box::new(custom::CustomCodeType::new(name.clone())),
        }
    }
}
impl AsCodeType for &'_ Type {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        (*self).as_codetype()
    }
}
impl AsCodeType for &&'_ Type {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        (**self).as_codetype()
    }
}
impl AsCodeType for &'_ Field {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ uniffi_bindgen::interface::Enum {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ uniffi_bindgen::interface::Object {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ Box<uniffi_meta::Type> {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ Argument {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ uniffi_bindgen::interface::Record {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}
impl AsCodeType for &'_ uniffi_bindgen::interface::CallbackInterface {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        self.as_type().as_codetype()
    }
}

mod filters {
    #![allow(unused_variables)]
    use super::*;
    use uniffi_meta::AsType;

    // Askama 0.14 passes a Values parameter to all filters. We use `_v` to accept but ignore it.

    pub(super) fn ffi_type(
        type_: &impl AsType,
        _v: &dyn askama::Values,
    ) -> Result<FfiType, askama::Error> {
        Ok(type_.as_type().into())
    }

    pub(super) fn type_name(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().type_label(ci, config))
    }

    /// Generate a fully qualified type name including the package.
    /// This is needed for enum variant fields to avoid naming collisions
    /// when a variant field type has the same name as the enum itself.
    pub(super) fn qualified_type_name<T>(
        as_type: &T,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error>
    where
        T: AsCodeType + AsType,
    {
        fully_qualified_type_label(&as_type.as_type(), ci, config)
            .map_err(|e| askama::Error::Custom(e.into()))
    }

    fn fully_qualified_type_label(
        ty: &Type,
        ci: &ComponentInterface,
        config: &Config,
    ) -> anyhow::Result<String> {
        match ty {
            Type::Optional { inner_type } => {
                let inner = fully_qualified_type_label(inner_type, ci, config)?;
                if config.nullness_annotations() {
                    Ok(format!("@org.jspecify.annotations.Nullable {}", inner))
                } else {
                    Ok(inner)
                }
            }
            Type::Sequence { inner_type } => match inner_type.as_ref() {
                Type::Int16 | Type::UInt16 => Ok("short[]".to_string()),
                Type::Int32 | Type::UInt32 => Ok("int[]".to_string()),
                Type::Int64 | Type::UInt64 => Ok("long[]".to_string()),
                Type::Float32 => Ok("float[]".to_string()),
                Type::Float64 => Ok("double[]".to_string()),
                Type::Boolean => Ok("boolean[]".to_string()),
                _ => Ok(format!(
                    "java.util.List<{}>",
                    fully_qualified_type_label(inner_type, ci, config)?
                )),
            },
            Type::Map {
                key_type,
                value_type,
            } => Ok(format!(
                "java.util.Map<{}, {}>",
                fully_qualified_type_label(key_type, ci, config)?,
                fully_qualified_type_label(value_type, ci, config)?
            )),
            Type::Enum { .. }
            | Type::Record { .. }
            | Type::Object { .. }
            | Type::CallbackInterface { .. }
            | Type::Custom { .. } => {
                let class_name = ty
                    .name()
                    .map(|nm| JavaCodeOracle.class_name(ci, nm))
                    .ok_or_else(|| anyhow::anyhow!("type {:?} has no name", ty))?;
                let package_name = package_for_type(ty, ci, config)?;
                Ok(format!("{}.{}", package_name, class_name))
            }
            _ => Ok(JavaCodeOracle.find(ty).type_label(ci, config)),
        }
    }

    fn package_for_type(
        ty: &Type,
        ci: &ComponentInterface,
        config: &Config,
    ) -> anyhow::Result<String> {
        if ci.is_external(ty) {
            let module_path = ty
                .module_path()
                .ok_or_else(|| anyhow::anyhow!("external type {:?} missing module path", ty))?;
            let namespace = ci.namespace_for_module_path(module_path)?;
            Ok(config.external_type_package_name(module_path, namespace))
        } else {
            Ok(config.package_name())
        }
    }

    /// Returns the [`ComponentInterface`] that owns `module_path`.
    ///
    /// If `module_path` belongs to the current crate, returns `ci` directly.
    /// Otherwise looks up an external component interface by the full module
    /// path first, then by the crate name (first `::` segment).
    fn component_interface_for_module_path<'a>(
        ci: &'a ComponentInterface,
        module_path: &str,
    ) -> Option<&'a ComponentInterface> {
        let crate_name = module_path.split("::").next().unwrap_or(module_path);
        if crate_name == ci.crate_name() {
            Some(ci)
        } else {
            ci.find_component_interface(module_path)
                .or_else(|| ci.find_component_interface(crate_name))
        }
    }

    pub(super) fn canonical_name(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().canonical_name())
    }

    /// Check if a type is external (from another crate)
    pub(super) fn is_external(
        as_type: &impl AsType,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<bool, askama::Error> {
        Ok(ci.is_external(&as_type.as_type()))
    }

    pub(super) fn ffi_converter_instance(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().ffi_converter_instance(config, ci))
    }

    pub(super) fn ffi_converter_name(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().ffi_converter_name())
    }

    pub(super) fn lower_fn(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.lower",
            as_ct.as_codetype().ffi_converter_instance(config, ci)
        ))
    }

    pub(super) fn allocation_size_fn(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.allocationSize",
            as_ct.as_codetype().ffi_converter_instance(config, ci)
        ))
    }

    pub(super) fn write_fn(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.write",
            as_ct.as_codetype().ffi_converter_instance(config, ci)
        ))
    }

    pub(super) fn lift_fn(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.lift",
            as_ct.as_codetype().ffi_converter_instance(config, ci)
        ))
    }

    pub(super) fn read_fn(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(format!(
            "{}.read",
            as_ct.as_codetype().ffi_converter_instance(config, ci)
        ))
    }

    // Get the idiomatic Java rendering of an integer.
    fn int_literal(t: &Option<Type>, base10: String) -> Result<String, askama::Error> {
        if let Some(t) = t {
            match t {
                // Byte and Short need explicit casts in Java
                Type::Int8 | Type::UInt8 => Ok(format!("(byte){}", base10)),
                Type::Int16 | Type::UInt16 => Ok(format!("(short){}", base10)),
                Type::Int32 | Type::UInt32 => Ok(base10),
                Type::Int64 | Type::UInt64 => Ok(base10),
                _ => Err(to_askama_error("Only ints are supported.")),
            }
        } else {
            Err(to_askama_error("Enum hasn't defined a repr"))
        }
    }

    // Get the idiomatic Java rendering of an individual enum variant's discriminant
    pub fn variant_discr_literal(
        e: &Enum,
        _v: &dyn askama::Values,
        index: &usize,
    ) -> Result<String, askama::Error> {
        let literal = e.variant_discr(*index).expect("invalid index");
        match literal {
            // Java doesn't convert between signed and unsigned by default
            // so we'll need to make sure we define the type as appropriately
            Literal::UInt(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            Literal::Int(v, _, _) => int_literal(e.variant_discr_type(), v.to_string()),
            _ => Err(to_askama_error("Only ints are supported.")),
        }
    }

    /// FFI type name (primitive for scalars, MemorySegment for everything else)
    pub fn ffi_type_name(
        type_: &FfiType,
        _v: &dyn askama::Values,
        config: &Config,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_type_label(type_, config, ci))
    }

    /// Returns the primitive call suffix (e.g. "Long", "Int") for primitive-specialized
    /// uniffiRustCall variants. Returns empty string for types where the high-level Java
    /// primitive doesn't match the FFI primitive (e.g. Boolean→byte) or non-primitive types.
    pub fn primitive_call_suffix(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        // Only bypass FfiConverter when the Java primitive matches the FFI primitive.
        // Boolean is excluded because it's `boolean` in Java but `byte` at the FFI level.
        Ok(
            match as_ct.as_codetype().type_label_primitive().as_deref() {
                Some("byte") => "Byte",
                Some("short") => "Short",
                Some("int") => "Int",
                Some("long") => "Long",
                Some("float") => "Float",
                Some("double") => "Double",
                _ => "",
            }
            .to_string(),
        )
    }

    /// Returns true if the argument's FFI type is a primitive where the Java type matches
    /// the FFI type directly (no conversion needed). Used to skip lower_fn for primitive args.
    /// Excludes boolean (Java `boolean` vs FFI `byte`).
    pub fn has_primitive_ffi_type(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
    ) -> Result<bool, askama::Error> {
        Ok(matches!(
            as_ct.as_codetype().type_label_primitive().as_deref(),
            Some("byte" | "short" | "int" | "long" | "float" | "double")
        ))
    }

    /// Maps FfiType to ValueLayout constant for FunctionDescriptor
    pub fn ffi_value_layout(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_value_layout(type_))
    }

    /// Generate the full structLayout body for an FfiStruct, with computed padding
    pub fn ffi_struct_layout_body(
        ffi_struct: &uniffi_bindgen::interface::FfiStruct,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        let mut parts = Vec::new();
        let mut offset: usize = 0;
        for field in ffi_struct.fields() {
            let field_type = field.type_();
            let alignment = JavaCodeOracle.ffi_type_alignment(&field_type);
            // Insert padding if needed for alignment
            let padding = if !offset.is_multiple_of(alignment) {
                alignment - (offset % alignment)
            } else {
                0
            };
            if padding > 0 {
                parts.push(format!(
                    "java.lang.foreign.MemoryLayout.paddingLayout({})",
                    padding
                ));
                offset += padding;
            }
            let layout = JavaCodeOracle.ffi_struct_field_layout(&field_type);
            let field_name = JavaCodeOracle.var_name_raw(field.name());
            parts.push(format!("{}.withName(\"{}\")", layout, field_name));
            // Advance offset
            let size = JavaCodeOracle.ffi_type_size(&field_type);
            if size > 0 {
                offset += size;
            } else {
                // Unknown size (e.g., user-defined FfiStruct) — can't compute further padding
                // but alignment was already handled
                offset = 0; // reset; further padding may be wrong but this is rare
            }
        }
        Ok(parts.join(",\n        "))
    }

    /// Maps FfiType to UNALIGNED ValueLayout for struct field access
    pub fn ffi_value_layout_unaligned(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_value_layout_unaligned(type_))
    }

    /// Cast prefix for invokeExact() return values
    pub fn ffi_invoke_exact_cast(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_invoke_exact_cast(type_))
    }

    /// Returns true if the FFI return type is a struct needing SegmentAllocator
    pub fn ffi_type_is_struct(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<bool, askama::Error> {
        Ok(JavaCodeOracle.ffi_type_is_struct(type_))
    }

    /// Returns true if this is an embedded struct (slice-based access in struct fields)
    pub fn ffi_type_is_embedded_struct(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<bool, askama::Error> {
        Ok(JavaCodeOracle.ffi_type_is_embedded_struct(type_))
    }

    /// Get the struct class name for an FFI struct type
    pub fn ffi_struct_type_name(
        type_: &FfiType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_struct_type_name(type_))
    }

    /// FFI type name using boxed types for generic contexts (accepts high-level Type)
    pub fn ffi_type_name_boxed(
        type_: &impl AsType,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        let ffi_type: FfiType = type_.as_type().into();
        Ok(JavaCodeOracle.ffi_type_label_boxed(&ffi_type))
    }

    /// Get the interface name for a trait implementation (for external trait interfaces).
    pub fn trait_interface_name(
        trait_ty: &Type,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let Some(module_path) = trait_ty.module_path() else {
            return Err(to_askama_error(&format!(
                "Invalid trait_type: {trait_ty:?}"
            )));
        };
        let Some(ci_look) = component_interface_for_module_path(ci, module_path) else {
            return Err(to_askama_error(&format!(
                "no interface with module_path: {}",
                module_path
            )));
        };

        let (obj_name, has_callback_interface) = match trait_ty {
            Type::Object { name, .. } => {
                let Some(obj) = ci_look.get_object_definition(name) else {
                    return Err(to_askama_error(&format!(
                        "trait interface not found: {}",
                        name
                    )));
                };
                (name, obj.has_callback_interface())
            }
            Type::CallbackInterface { name, .. } => (name, true),
            _ => {
                return Err(to_askama_error(&format!(
                    "Invalid trait_type: {trait_ty:?}"
                )));
            }
        };

        let class_name = JavaCodeOracle.class_name(ci_look, obj_name);
        if has_callback_interface {
            Ok(class_name)
        } else {
            Ok(format!("{}Interface", class_name))
        }
    }

    /// Get the idiomatic Java rendering of a class name from a string.
    pub fn class_name<S: AsRef<str>>(
        nm: S,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.class_name(ci, nm.as_ref()))
    }

    /// Get the idiomatic Java rendering of a class name from a Type.
    pub fn class_name_from_type(
        as_type: &impl AsType,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let type_ = as_type.as_type();
        let name = match &type_ {
            Type::Enum { name, .. } => name,
            Type::Object { name, .. } => name,
            Type::Record { name, .. } => name,
            Type::Custom { name, .. } => name,
            Type::CallbackInterface { name, .. } => name,
            _ => {
                return Err(to_askama_error(&format!(
                    "class_name_from_type: unsupported type {:?}",
                    type_
                )));
            }
        };
        Ok(JavaCodeOracle.class_name(ci, name))
    }

    /// Get the idiomatic Java rendering of a function name.
    pub fn fn_name<S: AsRef<str>>(nm: S, _v: &dyn askama::Values) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.fn_name(nm.as_ref()))
    }

    /// Get the idiomatic Java rendering of a variable name.
    pub fn var_name<S: AsRef<str>>(
        nm: S,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.var_name(nm.as_ref()))
    }

    /// Get the idiomatic Java rendering of a variable name, without altering reserved words.
    pub fn var_name_raw<S: AsRef<str>>(
        nm: S,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.var_name_raw(nm.as_ref()))
    }

    /// Get the idiomatic Java setter method name.
    pub fn setter<S: AsRef<str>>(nm: S, _v: &dyn askama::Values) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.setter(nm.as_ref()))
    }

    /// Get a String representing the name used for an individual enum variant.
    pub fn variant_name(
        variant: &Variant,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.enum_variant_name(variant.name()))
    }

    pub fn error_variant_name(
        variant: &Variant,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        let name = variant.name().to_string().to_upper_camel_case();
        Ok(JavaCodeOracle.convert_error_suffix(&name))
    }

    /// Get the idiomatic Java rendering of an FFI callback function name
    pub fn ffi_callback_name<S: AsRef<str>>(
        nm: S,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_callback_name(nm.as_ref()))
    }

    /// Get the idiomatic Java rendering of an FFI struct name
    pub fn ffi_struct_name<S: AsRef<str>>(
        nm: S,
        _v: &dyn askama::Values,
    ) -> Result<String, askama::Error> {
        Ok(JavaCodeOracle.ffi_struct_name(nm.as_ref()))
    }

    pub fn object_names(
        obj: &Object,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<(String, String), askama::Error> {
        Ok(JavaCodeOracle.object_names(ci, obj))
    }

    pub fn async_inner_return_type(
        callable: impl Callable,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        callable
            .return_type()
            .map_or(Ok("java.lang.Void".to_string()), |t| {
                type_name(t, _v, ci, config)
            })
    }

    pub fn async_return_type(
        callable: impl Callable,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        let is_async = callable.is_async();
        let inner_type = async_inner_return_type(callable, _v, ci, config)?;
        if is_async {
            Ok(format!(
                "java.util.concurrent.CompletableFuture<{inner_type}>"
            ))
        } else {
            Ok(inner_type)
        }
    }

    pub fn async_poll(
        callable: impl Callable,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_poll(ci);
        Ok(format!(
            "(future, callback, continuationHandle) -> UniffiLib.{ffi_func}(future, callback, continuationHandle)"
        ))
    }

    pub fn async_complete(
        callable: impl Callable,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        _config: &Config,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_complete(ci);
        // The complete function returns a RustBuffer for types that use RustBuffer FFI,
        // which is a struct and needs a SegmentAllocator.
        let needs_allocator = callable.return_type().is_some_and(|t| {
            let ffi_type: FfiType = t.into();
            JavaCodeOracle.ffi_type_is_struct(&ffi_type)
        });
        let allocator_arg = if needs_allocator { "_allocator, " } else { "" };
        let call = format!("UniffiLib.{ffi_func}({allocator_arg}future, continuation)");
        Ok(format!("(_allocator, future, continuation) -> {call}"))
    }

    pub fn async_free(
        callable: impl Callable,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
    ) -> Result<String, askama::Error> {
        let ffi_func = callable.ffi_rust_future_free(ci);
        Ok(format!("(future) -> UniffiLib.{ffi_func}(future)"))
    }

    /// Remove the "`" chars we put around function/variable names
    ///
    /// These are used to avoid name clashes with java identifiers, but sometimes you want to
    /// render the name unquoted.  One example is the message property for errors where we want to
    /// display the name for the user.
    pub fn unquote<S: AsRef<str>>(nm: S, _v: &dyn askama::Values) -> Result<String, askama::Error> {
        Ok(nm.as_ref().trim_matches('`').to_string())
    }

    /// Get the idiomatic Java rendering of docstring
    pub fn docstring<S: AsRef<str>>(
        docstring: S,
        _v: &dyn askama::Values,
        spaces: &i32,
    ) -> Result<String, askama::Error> {
        let middle = textwrap::indent(&textwrap::dedent(docstring.as_ref()), " * ");
        let wrapped = format!("/**\n{middle}\n */");

        let spaces = usize::try_from(*spaces).unwrap_or_default();
        Ok(textwrap::indent(&wrapped, &" ".repeat(spaces)))
    }

    /// Returns the type name suitable for use in field declarations, method parameters, and return types.
    /// For non-optional primitives, returns the primitive type (int, long, boolean, etc.).
    /// For optional types and all other types, returns the boxed/object type.
    pub fn type_name_for_field(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        // Check if the codetype has a primitive label available
        let codetype = as_ct.as_codetype();
        if let Some(primitive) = codetype.type_label_primitive() {
            return Ok(primitive);
        }
        // Otherwise use the standard boxed type label
        Ok(codetype.type_label(ci, config))
    }

    /// Always returns the boxed type name, for use in generic contexts like CompletableFuture<T>.
    /// This is the same as type_name but with a clearer name for template readability.
    pub fn boxed_type_name(
        as_ct: &impl AsCodeType,
        _v: &dyn askama::Values,
        ci: &ComponentInterface,
        config: &Config,
    ) -> Result<String, askama::Error> {
        Ok(as_ct.as_codetype().type_label(ci, config))
    }

    /// Generates an equality expression for comparing two values of a field's type.
    /// For primitives: returns "left == right"
    /// For objects: returns "java.util.Objects.equals(left, right)"
    pub fn equals_expr<T: AsCodeType, L: std::fmt::Display, R: std::fmt::Display>(
        field: &T,
        _v: &dyn askama::Values,
        left: L,
        right: R,
    ) -> Result<String, askama::Error> {
        // Check if this type has a primitive label (meaning it's a primitive)
        if field.as_codetype().type_label_primitive().is_some() {
            Ok(format!("{} == {}", left, right))
        } else {
            Ok(format!("java.util.Objects.equals({}, {})", left, right))
        }
    }

    /// Generates a hash code expression for a field value.
    /// For primitives: returns "Type.hashCode(value)" (e.g., "java.lang.Integer.hashCode(value)")
    /// For objects: returns "java.util.Objects.hashCode(value)"
    pub fn hash_code_expr<T: AsCodeType + AsType, V: std::fmt::Display>(
        field: &T,
        _v: &dyn askama::Values,
        value: V,
    ) -> Result<String, askama::Error> {
        match field.as_type() {
            Type::Boolean => Ok(format!("java.lang.Boolean.hashCode({})", value)),
            Type::Int8 | Type::UInt8 => Ok(format!("java.lang.Byte.hashCode({})", value)),
            Type::Int16 | Type::UInt16 => Ok(format!("java.lang.Short.hashCode({})", value)),
            Type::Int32 | Type::UInt32 => Ok(format!("java.lang.Integer.hashCode({})", value)),
            Type::Int64 | Type::UInt64 => Ok(format!("java.lang.Long.hashCode({})", value)),
            Type::Float32 => Ok(format!("java.lang.Float.hashCode({})", value)),
            Type::Float64 => Ok(format!("java.lang.Double.hashCode({})", value)),
            _ => Ok(format!("java.util.Objects.hashCode({})", value)),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uniffi_bindgen::interface::ComponentInterface;
    use uniffi_meta::{
        CallbackInterfaceMetadata, EnumMetadata, EnumShape, FieldMetadata, FnMetadata,
        FnParamMetadata, Metadata, MetadataGroup, MethodMetadata, NamespaceMetadata, ObjectImpl,
        ObjectMetadata, ObjectTraitImplMetadata, RecordMetadata, TraitMethodMetadata, Type,
        VariantMetadata,
    };

    #[test]
    fn preserves_error_type_named_error() {
        // create a metadata group with an error enum named only "Error" and a
        // function that always returns that error.
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };
        group.add_item(Metadata::Enum(EnumMetadata {
            module_path: "test".to_string(),
            name: "Error".to_string(),
            shape: EnumShape::Error { flat: true },
            remote: false,
            variants: vec![VariantMetadata {
                name: "Oops".to_string(),
                discr: None,
                fields: vec![],
                docstring: None,
            }],
            discr_type: None,
            non_exhaustive: false,
            docstring: None,
        }));
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "always_fails".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: None,
            throws: Some(Type::Enum {
                module_path: "test".to_string(),
                name: "Error".to_string(),
            }),
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        // show that the generated bindings preserve the name
        // Note: we use java.lang.Exception to avoid collisions with user types
        let relevant_lines = bindings
            .lines()
            .filter(|line| {
                line.contains("class Error extends java.lang.Exception")
                    || line.contains("class Exception extends java.lang.Exception")
                    || line.contains("throws Error")
                    || line.contains("throws Exception")
            })
            .collect::<Vec<_>>()
            .join("\n");

        assert!(
            bindings.contains("public class Error extends java.lang.Exception"),
            "expected generated bindings to preserve the `Error` type name:\n{relevant_lines}"
        );
        assert!(
            bindings.contains("public static void alwaysFails() throws Error"),
            "expected generated bindings to preserve `throws Error`:\n{relevant_lines}"
        );
    }

    #[test]
    fn converts_error_suffix_but_not_bare_error() {
        assert_eq!(
            JavaCodeOracle.convert_error_suffix("ExampleError"),
            "ExampleException"
        );
        assert_eq!(JavaCodeOracle.convert_error_suffix("Error"), "Error");
    }

    fn create_primitive_array_test_group() -> MetadataGroup {
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };

        // Add functions for each primitive array type
        let primitive_types = [
            ("process_floats", Type::Float32),
            ("process_doubles", Type::Float64),
            ("process_shorts", Type::Int16),
            ("process_ints", Type::Int32),
            ("process_longs", Type::Int64),
            ("process_bools", Type::Boolean),
        ];

        for (name, inner_type) in primitive_types {
            group.add_item(Metadata::Func(FnMetadata {
                module_path: "test".to_string(),
                name: name.to_string(),
                is_async: false,
                inputs: vec![FnParamMetadata {
                    name: "data".to_string(),
                    ty: Type::Sequence {
                        inner_type: Box::new(inner_type.clone()),
                    },
                    by_ref: false,
                    optional: false,
                    default: None,
                }],
                return_type: Some(Type::Sequence {
                    inner_type: Box::new(inner_type),
                }),
                throws: None,
                checksum: None,
                docstring: None,
            }));
        }

        group
    }

    #[test]
    fn generates_float32_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("float[]"),
            "expected float[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterFloat32Array"),
            "expected FfiConverterFloat32Array in generated bindings"
        );
        assert!(
            bindings.contains("public static float[] processFloats(float[] data)"),
            "expected processFloats method with float[] signature"
        );
    }

    #[test]
    fn generates_float64_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("double[]"),
            "expected double[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterFloat64Array"),
            "expected FfiConverterFloat64Array in generated bindings"
        );
        assert!(
            bindings.contains("public static double[] processDoubles(double[] data)"),
            "expected processDoubles method with double[] signature"
        );
    }

    #[test]
    fn generates_int32_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("int[]"),
            "expected int[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterInt32Array"),
            "expected FfiConverterInt32Array in generated bindings"
        );
        assert!(
            bindings.contains("public static int[] processInts(int[] data)"),
            "expected processInts method with int[] signature"
        );
    }

    #[test]
    fn generates_int64_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("long[]"),
            "expected long[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterInt64Array"),
            "expected FfiConverterInt64Array in generated bindings"
        );
        assert!(
            bindings.contains("public static long[] processLongs(long[] data)"),
            "expected processLongs method with long[] signature"
        );
    }

    #[test]
    fn generates_int16_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("short[]"),
            "expected short[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterInt16Array"),
            "expected FfiConverterInt16Array in generated bindings"
        );
        assert!(
            bindings.contains("public static short[] processShorts(short[] data)"),
            "expected processShorts method with short[] signature"
        );
    }

    #[test]
    fn generates_boolean_primitive_array() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("boolean[]"),
            "expected boolean[] in generated bindings"
        );
        assert!(
            bindings.contains("FfiConverterBooleanArray"),
            "expected FfiConverterBooleanArray in generated bindings"
        );
        assert!(
            bindings.contains("public static boolean[] processBools(boolean[] data)"),
            "expected processBools method with boolean[] signature"
        );
    }

    #[test]
    fn does_not_generate_list_for_primitive_sequences() {
        let group = create_primitive_array_test_group();
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        // Verify that we don't generate boxed List types for primitives
        assert!(
            !bindings.contains("List<java.lang.Float>"),
            "should not contain List<java.lang.Float>"
        );
        assert!(
            !bindings.contains("List<java.lang.Double>"),
            "should not contain List<java.lang.Double>"
        );
        assert!(
            !bindings.contains("List<java.lang.Integer>"),
            "should not contain List<java.lang.Integer>"
        );
        assert!(
            !bindings.contains("List<java.lang.Long>"),
            "should not contain List<java.lang.Long>"
        );
        assert!(
            !bindings.contains("List<java.lang.Short>"),
            "should not contain List<java.lang.Short>"
        );
        assert!(
            !bindings.contains("List<java.lang.Boolean>"),
            "should not contain List<java.lang.Boolean>"
        );
    }

    #[test]
    fn android_replaces_ffm_package() {
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "noop".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: None,
            throws: None,
            checksum: None,
            docstring: None,
        }));

        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();

        let android_config: Config = toml::from_str("android = true").unwrap();

        let bindings = generate_bindings(&android_config, &ci).unwrap();

        assert!(
            !bindings.contains("java.lang.foreign."),
            "android bindings should not contain java.lang.foreign"
        );
        assert!(
            bindings.contains("com.v7878.foreign."),
            "android bindings should contain com.v7878.foreign"
        );
        // MethodHandle/MethodHandles/MethodType should NOT be replaced
        assert!(
            bindings.contains("java.lang.invoke.MethodHandle"),
            "android bindings should preserve java.lang.invoke.MethodHandle"
        );
        // Standard java.lang types should NOT be replaced
        assert!(
            bindings.contains("java.lang.Exception"),
            "android bindings should preserve java.lang.Exception"
        );
    }

    #[test]
    fn trait_impl_with_submodule_path() {
        // Regression test: when a crate has multiple modules, module_path is
        // "crate_name::submodule". component_interface_for_module_path() handles
        // this by checking the local CI's crate_name first before falling back
        // to find_component_interface() for external crates.
        let submodule_path = "mycrate::inner";
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "mycrate".to_string(),
                name: "mycrate".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };

        // A trait object defined in a submodule
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: submodule_path.to_string(),
            name: "MyTrait".to_string(),
            remote: false,
            imp: ObjectImpl::CallbackTrait,
            docstring: None,
        }));

        // A concrete object that implements the trait, also in the submodule
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: submodule_path.to_string(),
            name: "MyObj".to_string(),
            remote: false,
            imp: ObjectImpl::Struct,
            docstring: None,
        }));

        group.add_item(Metadata::ObjectTraitImpl(ObjectTraitImplMetadata {
            ty: Type::Object {
                module_path: submodule_path.to_string(),
                name: "MyObj".to_string(),
                imp: ObjectImpl::Struct,
            },
            trait_ty: Type::Object {
                module_path: submodule_path.to_string(),
                name: "MyTrait".to_string(),
                imp: ObjectImpl::CallbackTrait,
            },
        }));

        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        // The object should implement the trait interface
        assert!(
            bindings.contains("implements AutoCloseable, MyObjInterface, MyTrait"),
            "MyObj should implement MyTrait via trait_interface_name even with submodule path:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("MyObj") || l.contains("MyTrait"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn local_trait_impls_accept_submodule_module_paths() {
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: "test".to_string(),
            name: "DefaultMetricsRecorder".to_string(),
            remote: false,
            imp: ObjectImpl::Struct,
            docstring: None,
        }));
        group.add_item(Metadata::CallbackInterface(CallbackInterfaceMetadata {
            module_path: "test::metrics".to_string(),
            name: "MetricsRecorder".to_string(),
            docstring: None,
        }));
        group.add_item(Metadata::ObjectTraitImpl(ObjectTraitImplMetadata {
            ty: Type::Object {
                module_path: "test".to_string(),
                name: "DefaultMetricsRecorder".to_string(),
                imp: ObjectImpl::Struct,
            },
            trait_ty: Type::CallbackInterface {
                module_path: "test::metrics".to_string(),
                name: "MetricsRecorder".to_string(),
            },
        }));

        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();

        let interface_name = super::filters::trait_interface_name(
            &Type::CallbackInterface {
                module_path: "test::metrics".to_string(),
                name: "MetricsRecorder".to_string(),
            },
            &(),
            &ci,
        )
        .unwrap();
        assert_eq!(interface_name, "MetricsRecorder");

        let bindings = generate_bindings(&Config::default(), &ci).unwrap();
        assert!(
            bindings.contains("DefaultMetricsRecorderInterface, MetricsRecorder"),
            "expected local callback trait impls with submodule paths to render successfully:\n{}",
            bindings
                .lines()
                .filter(|line| line.contains("class DefaultMetricsRecorder"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    fn nullness_config() -> Config {
        toml::from_str("nullness_annotations = true").unwrap()
    }

    fn test_group() -> MetadataGroup {
        MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        }
    }

    #[test]
    fn nullness_annotations_disabled_by_default() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "maybe_string".to_string(),
            is_async: false,
            inputs: vec![FnParamMetadata {
                name: "input".to_string(),
                ty: Type::String,
                by_ref: false,
                optional: false,
                default: None,
            }],
            return_type: Some(Type::Optional {
                inner_type: Box::new(Type::String),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();
        assert!(
            !bindings.contains("@org.jspecify.annotations.NullMarked"),
            "should not contain @NullMarked when disabled"
        );
        assert!(
            !bindings.contains("@org.jspecify.annotations.Nullable"),
            "should not contain @Nullable when disabled"
        );
    }

    #[test]
    fn nullness_function_with_optional_param_and_return() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "foo".to_string(),
            is_async: false,
            inputs: vec![
                FnParamMetadata {
                    name: "required".to_string(),
                    ty: Type::String,
                    by_ref: false,
                    optional: false,
                    default: None,
                },
                FnParamMetadata {
                    name: "optional".to_string(),
                    ty: Type::Optional {
                        inner_type: Box::new(Type::String),
                    },
                    by_ref: false,
                    optional: false,
                    default: None,
                },
            ],
            return_type: Some(Type::Optional {
                inner_type: Box::new(Type::Int32),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable java.lang.Integer"),
            "return type should be @Nullable Integer"
        );
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable java.lang.String optional"),
            "optional param should be @Nullable String"
        );
        // The required param should NOT have @Nullable
        assert!(
            bindings.contains("java.lang.String required, @org.jspecify.annotations.Nullable"),
            "required param should not have @Nullable"
        );
    }

    #[test]
    fn nullness_record_with_optional_field() {
        let mut group = test_group();
        group.add_item(Metadata::Record(RecordMetadata {
            module_path: "test".to_string(),
            name: "Person".to_string(),
            remote: false,
            fields: vec![
                FieldMetadata {
                    name: "name".to_string(),
                    ty: Type::String,
                    default: None,
                    docstring: None,
                },
                FieldMetadata {
                    name: "nickname".to_string(),
                    ty: Type::Optional {
                        inner_type: Box::new(Type::String),
                    },
                    default: None,
                    docstring: None,
                },
            ],
            docstring: None,
        }));
        // Need a function to make the record reachable
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "get_person".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: Some(Type::Record {
                module_path: "test".to_string(),
                name: "Person".to_string(),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings
                .contains("private @org.jspecify.annotations.Nullable java.lang.String nickname"),
            "optional field should have @Nullable:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("nickname"))
                .collect::<Vec<_>>()
                .join("\n")
        );
        // Non-optional field should NOT have @Nullable
        assert!(
            !bindings.contains("@org.jspecify.annotations.Nullable java.lang.String name"),
            "non-optional field should not have @Nullable"
        );
    }

    #[test]
    fn nullness_immutable_record_with_optional_field() {
        let mut group = test_group();
        group.add_item(Metadata::Record(RecordMetadata {
            module_path: "test".to_string(),
            name: "Person".to_string(),
            remote: false,
            fields: vec![
                FieldMetadata {
                    name: "name".to_string(),
                    ty: Type::String,
                    default: None,
                    docstring: None,
                },
                FieldMetadata {
                    name: "nickname".to_string(),
                    ty: Type::Optional {
                        inner_type: Box::new(Type::String),
                    },
                    default: None,
                    docstring: None,
                },
            ],
            docstring: None,
        }));
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "get_person".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: Some(Type::Record {
                module_path: "test".to_string(),
                name: "Person".to_string(),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let config: Config =
            toml::from_str("nullness_annotations = true\ngenerate_immutable_records = true")
                .unwrap();
        let bindings = generate_bindings(&config, &ci).unwrap();
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable java.lang.String nickname"),
            "immutable record should have @Nullable on optional component:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("nickname"))
                .collect::<Vec<_>>()
                .join("\n")
        );
        assert!(
            !bindings.contains("@org.jspecify.annotations.Nullable java.lang.String name"),
            "non-optional component should not have @Nullable"
        );
    }

    #[test]
    fn nullness_object_method_with_optional_param() {
        let mut group = test_group();
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: "test".to_string(),
            name: "MyObj".to_string(),
            remote: false,
            imp: ObjectImpl::Struct,
            docstring: None,
        }));
        group.add_item(Metadata::Method(MethodMetadata {
            module_path: "test".to_string(),
            self_name: "MyObj".to_string(),
            name: "do_thing".to_string(),
            is_async: false,
            inputs: vec![FnParamMetadata {
                name: "input".to_string(),
                ty: Type::Optional {
                    inner_type: Box::new(Type::String),
                },
                by_ref: false,
                optional: false,
                default: None,
            }],
            return_type: None,
            throws: None,
            takes_self_by_arc: false,
            checksum: None,
            docstring: None,
        }));
        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable java.lang.String input"),
            "object method param should have @Nullable:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("doThing"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn nullness_object_cleanable_field_annotated() {
        let mut group = test_group();
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: "test".to_string(),
            name: "MyObj".to_string(),
            remote: false,
            imp: ObjectImpl::Struct,
            docstring: None,
        }));
        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings
                .contains("@org.jspecify.annotations.Nullable UniffiCleaner.Cleanable cleanable"),
            "cleanable field should have @Nullable when annotations enabled:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("cleanable"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn nullness_object_cleanable_field_not_annotated_by_default() {
        let mut group = test_group();
        group.add_item(Metadata::Object(ObjectMetadata {
            module_path: "test".to_string(),
            name: "MyObj".to_string(),
            remote: false,
            imp: ObjectImpl::Struct,
            docstring: None,
        }));
        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();
        let bindings = generate_bindings(&Config::default(), &ci).unwrap();
        assert!(
            bindings.contains("UniffiCleaner.Cleanable cleanable"),
            "cleanable field should not have @Nullable by default"
        );
        assert!(
            !bindings.contains("@org.jspecify.annotations.Nullable UniffiCleaner.Cleanable"),
            "cleanable field should not have @Nullable when annotations disabled"
        );
    }

    #[test]
    fn nullness_enum_variant_with_optional_field() {
        let mut group = test_group();
        group.add_item(Metadata::Enum(EnumMetadata {
            module_path: "test".to_string(),
            name: "MyEnum".to_string(),
            shape: EnumShape::Enum,
            remote: false,
            variants: vec![VariantMetadata {
                name: "WithOptional".to_string(),
                discr: None,
                fields: vec![FieldMetadata {
                    name: "value".to_string(),
                    ty: Type::Optional {
                        inner_type: Box::new(Type::String),
                    },
                    default: None,
                    docstring: None,
                }],
                docstring: None,
            }],
            discr_type: None,
            non_exhaustive: false,
            docstring: None,
        }));
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "get_enum".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: Some(Type::Enum {
                module_path: "test".to_string(),
                name: "MyEnum".to_string(),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable"),
            "enum variant with optional field should have @Nullable:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("value"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn nullness_error_variant_with_optional_field() {
        let mut group = test_group();
        group.add_item(Metadata::Enum(EnumMetadata {
            module_path: "test".to_string(),
            name: "MyError".to_string(),
            shape: EnumShape::Error { flat: false },
            remote: false,
            variants: vec![VariantMetadata {
                name: "BadInput".to_string(),
                discr: None,
                fields: vec![FieldMetadata {
                    name: "detail".to_string(),
                    ty: Type::Optional {
                        inner_type: Box::new(Type::String),
                    },
                    default: None,
                    docstring: None,
                }],
                docstring: None,
            }],
            discr_type: None,
            non_exhaustive: false,
            docstring: None,
        }));
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "do_stuff".to_string(),
            is_async: false,
            inputs: vec![],
            return_type: None,
            throws: Some(Type::Enum {
                module_path: "test".to_string(),
                name: "MyError".to_string(),
            }),
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings.contains("@org.jspecify.annotations.Nullable"),
            "error variant with optional field should have @Nullable:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("detail"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn nullness_async_function_with_optional_return() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "fetch".to_string(),
            is_async: true,
            inputs: vec![],
            return_type: Some(Type::Optional {
                inner_type: Box::new(Type::String),
            }),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings
                .contains("CompletableFuture<@org.jspecify.annotations.Nullable java.lang.String>"),
            "async optional return should be CompletableFuture<@Nullable String>:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("fetch"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn nullness_non_optional_types_never_nullable() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "identity".to_string(),
            is_async: false,
            inputs: vec![
                FnParamMetadata {
                    name: "s".to_string(),
                    ty: Type::String,
                    by_ref: false,
                    optional: false,
                    default: None,
                },
                FnParamMetadata {
                    name: "i".to_string(),
                    ty: Type::Int32,
                    by_ref: false,
                    optional: false,
                    default: None,
                },
                FnParamMetadata {
                    name: "b".to_string(),
                    ty: Type::Boolean,
                    by_ref: false,
                    optional: false,
                    default: None,
                },
            ],
            return_type: Some(Type::String),
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            !bindings.contains("@org.jspecify.annotations.Nullable"),
            "non-optional types should never have @Nullable"
        );
    }

    #[test]
    fn nullness_nested_optional_in_map_value() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "process_map".to_string(),
            is_async: false,
            inputs: vec![FnParamMetadata {
                name: "data".to_string(),
                ty: Type::Map {
                    key_type: Box::new(Type::String),
                    value_type: Box::new(Type::Optional {
                        inner_type: Box::new(Type::Int32),
                    }),
                },
                by_ref: false,
                optional: false,
                default: None,
            }],
            return_type: None,
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings.contains("java.util.Map<java.lang.String, @org.jspecify.annotations.Nullable java.lang.Integer>"),
            "map with optional value should have @Nullable on value type:\n{}",
            bindings.lines().filter(|l| l.contains("processMap")).collect::<Vec<_>>().join("\n")
        );
    }

    #[test]
    fn nullness_nested_optional_in_list() {
        let mut group = test_group();
        group.add_item(Metadata::Func(FnMetadata {
            module_path: "test".to_string(),
            name: "process_list".to_string(),
            is_async: false,
            inputs: vec![FnParamMetadata {
                name: "data".to_string(),
                ty: Type::Sequence {
                    inner_type: Box::new(Type::Optional {
                        inner_type: Box::new(Type::String),
                    }),
                },
                by_ref: false,
                optional: false,
                default: None,
            }],
            return_type: None,
            throws: None,
            checksum: None,
            docstring: None,
        }));
        let ci = ComponentInterface::from_metadata(group).unwrap();
        let bindings = generate_bindings(&nullness_config(), &ci).unwrap();
        assert!(
            bindings
                .contains("java.util.List<@org.jspecify.annotations.Nullable java.lang.String>"),
            "list with optional element should have @Nullable on element type:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("processList"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }

    #[test]
    fn callback_interface_helpers_use_class_style_names() {
        let mut group = MetadataGroup {
            namespace: NamespaceMetadata {
                crate_name: "test".to_string(),
                name: "test".to_string(),
            },
            namespace_docstring: None,
            items: Default::default(),
        };
        group.add_item(Metadata::CallbackInterface(CallbackInterfaceMetadata {
            module_path: "test".to_string(),
            name: "Histogram".to_string(),
            docstring: None,
        }));
        group.add_item(Metadata::TraitMethod(TraitMethodMetadata {
            module_path: "test".to_string(),
            trait_name: "Histogram".to_string(),
            index: 0,
            name: "record".to_string(),
            is_async: false,
            inputs: vec![FnParamMetadata {
                name: "value".to_string(),
                ty: Type::Float64,
                by_ref: false,
                optional: false,
                default: None,
            }],
            return_type: None,
            throws: None,
            takes_self_by_arc: false,
            checksum: None,
            docstring: None,
        }));

        let mut ci = ComponentInterface::from_metadata(group).unwrap();
        ci.derive_ffi_funcs().unwrap();

        let bindings = generate_bindings(&Config::default(), &ci).unwrap();

        assert!(
            bindings.contains("public void record(double value);"),
            "expected callback interface API to preserve the Rust method name:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("double value"))
                .collect::<Vec<_>>()
                .join("\n")
        );
        assert!(
            bindings.contains("public static final class RecordCallback implements UniffiCallbackInterfaceHistogramMethod0.Fn"),
            "expected callback helper class to use a Java class-style name:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("implements UniffiCallbackInterfaceHistogramMethod0.Fn"))
                .collect::<Vec<_>>()
                .join("\n")
        );
        assert!(
            bindings.contains("RecordCallback.INSTANCE"),
            "expected generated callback helper references to use the renamed helper class:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains(".INSTANCE"))
                .collect::<Vec<_>>()
                .join("\n")
        );
        assert!(
            !bindings.contains("public static class record implements"),
            "unexpected lowercase helper class leaked into generated bindings:\n{}",
            bindings
                .lines()
                .filter(|l| l.contains("class record"))
                .collect::<Vec<_>>()
                .join("\n")
        );
    }
}
