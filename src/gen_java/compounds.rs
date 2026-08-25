/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{AsCodeType, CodeType, Config};
use uniffi_bindgen::interface::ComponentInterface;
use uniffi_meta::Type;

#[derive(Debug)]
pub struct OptionalCodeType {
    inner: Type,
}

impl OptionalCodeType {
    pub fn new(inner: Type) -> Self {
        Self { inner }
    }
    fn inner(&self) -> &Type {
        &self.inner
    }
}

impl CodeType for OptionalCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        let inner = super::JavaCodeOracle
            .find(self.inner())
            .type_label(ci, config);
        if config.nullness_annotations() {
            super::nullable_type_label(&inner)
        } else {
            inner
        }
    }

    fn canonical_name(&self) -> String {
        format!(
            "Optional{}",
            super::JavaCodeOracle.find(self.inner()).canonical_name()
        )
    }
}

#[derive(Debug)]
pub struct SequenceCodeType {
    inner: Type,
}

impl SequenceCodeType {
    pub fn new(inner: Type) -> Self {
        Self { inner }
    }
    fn inner(&self) -> &Type {
        &self.inner
    }
}

impl CodeType for SequenceCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        format!(
            "java.util.List<{}>",
            super::JavaCodeOracle
                .find(self.inner())
                .type_label(ci, config)
        )
    }

    fn canonical_name(&self) -> String {
        format!(
            "Sequence{}",
            super::JavaCodeOracle.find(self.inner()).canonical_name()
        )
    }
}

#[derive(Debug)]
pub struct SetCodeType {
    inner: Type,
}

impl SetCodeType {
    pub fn new(inner: Type) -> Self {
        Self { inner }
    }
    fn inner(&self) -> &Type {
        &self.inner
    }
}

impl CodeType for SetCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        format!(
            "java.util.Set<{}>",
            Hashed(self.inner()).as_codetype().type_label(ci, config)
        )
    }

    fn canonical_name(&self) -> String {
        format!("Set{}", Hashed(self.inner()).as_codetype().canonical_name())
    }
}

#[derive(Debug)]
pub struct MapCodeType {
    key: Type,
    value: Type,
}

impl MapCodeType {
    pub fn new(key: Type, value: Type) -> Self {
        Self { key, value }
    }

    fn key(&self) -> &Type {
        &self.key
    }

    fn value(&self) -> &Type {
        &self.value
    }
}

impl CodeType for MapCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        format!(
            "java.util.Map<{}, {}>",
            Hashed(self.key()).as_codetype().type_label(ci, config),
            super::JavaCodeOracle
                .find(self.value())
                .type_label(ci, config),
        )
    }

    fn canonical_name(&self) -> String {
        format!(
            "Map{}{}",
            Hashed(self.key()).as_codetype().canonical_name(),
            self.value().as_codetype().canonical_name(),
        )
    }
}

/// A type in a position Java will hash: a `Set` element or a `Map` key.
///
/// Java arrays hash and compare by identity, so the primitive-array lens would leave
/// `Set.contains`/`Map.get` never matching, and would let value-equal entries coexist that Rust
/// deduplicates. Hashed positions render every array-producing type on the `Sequence`/`Optional`
/// spine as boxed `java.util.List<T>` instead, however deep, and `bytes` as
/// `java.util.List<java.lang.Byte>`. That spine is exhaustive: Rust's `Hash + Eq` bounds keep
/// `HashMap`/`HashSet` and float vectors out of hashed positions, and uniffi 0.32.0 has no
/// converters for the `BTree` collections that would otherwise qualify.
///
/// `bytes` and `Vec<i8>` share a wire format (i32 length + raw bytes), so a hashed `bytes` can
/// borrow the generic `Sequence<i8>` converter unchanged.
///
/// [`super::render_one_type`] emits the hashed converter variants alongside the plain ones for
/// any type this applies to.
#[derive(Debug)]
pub struct Hashed<'a>(pub &'a Type);

impl AsCodeType for Hashed<'_> {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        match self.0 {
            ty if !needs_hashed_rendering(ty) => ty.as_codetype(),
            Type::Sequence { inner_type } => {
                Box::new(HashedSequenceCodeType::new((**inner_type).clone()))
            }
            Type::Optional { inner_type } => {
                Box::new(HashedOptionalCodeType::new((**inner_type).clone()))
            }
            Type::Bytes => Box::new(SequenceCodeType::new(Type::Int8)),
            _ => unreachable!("needs_hashed_rendering matches only Sequence, Optional, and Bytes"),
        }
    }
}

/// Whether `ty`'s plain rendering puts a Java array anywhere value equality would consult it:
/// directly, or under the `Sequence`/`Optional` wrappers [`Hashed`] recurses through.
pub fn needs_hashed_rendering(ty: &Type) -> bool {
    match ty {
        Type::Bytes => true,
        Type::Sequence { inner_type } => {
            renders_as_primitive_array(inner_type) || needs_hashed_rendering(inner_type)
        }
        Type::Optional { inner_type } => needs_hashed_rendering(inner_type),
        _ => false,
    }
}

/// [`SequenceCodeType`] with the inner type rendered through [`Hashed`].
#[derive(Debug)]
pub struct HashedSequenceCodeType {
    inner: Type,
}

impl HashedSequenceCodeType {
    pub fn new(inner: Type) -> Self {
        Self { inner }
    }
}

impl CodeType for HashedSequenceCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        format!(
            "java.util.List<{}>",
            Hashed(&self.inner).as_codetype().type_label(ci, config)
        )
    }

    fn canonical_name(&self) -> String {
        format!(
            "Sequence{}",
            Hashed(&self.inner).as_codetype().canonical_name()
        )
    }
}

/// [`OptionalCodeType`] with the inner type rendered through [`Hashed`].
#[derive(Debug)]
pub struct HashedOptionalCodeType {
    inner: Type,
}

impl HashedOptionalCodeType {
    pub fn new(inner: Type) -> Self {
        Self { inner }
    }
}

impl CodeType for HashedOptionalCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        let inner = Hashed(&self.inner).as_codetype().type_label(ci, config);
        if config.nullness_annotations() {
            super::nullable_type_label(&inner)
        } else {
            inner
        }
    }

    fn canonical_name(&self) -> String {
        format!(
            "Optional{}",
            Hashed(&self.inner).as_codetype().canonical_name()
        )
    }
}

/// Whether `ty`'s rendering holds a Java array anywhere `equals`/`hashCode` would visit:
/// directly, or inside `Sequence`/`Optional` wrappers or `Map` values. Fields for which this
/// holds compare via `UniffiDeepValue` instead of `Objects.equals`. `Set` elements and `Map`
/// keys need no recursion: they are hashed positions, already array-free.
pub fn contains_array_rendering(ty: &Type) -> bool {
    match ty {
        Type::Bytes => true,
        Type::Sequence { inner_type } => {
            renders_as_primitive_array(inner_type) || contains_array_rendering(inner_type)
        }
        Type::Optional { inner_type } => contains_array_rendering(inner_type),
        Type::Map { value_type, .. } => contains_array_rendering(value_type),
        _ => false,
    }
}

/// The code type for `Vec<inner>` when it renders as a Java primitive array.
///
/// `Int8`/`UInt8` are absent because the separate `Bytes` type owns `byte[]`.
pub fn primitive_array_code_type(inner: &Type) -> Option<Box<dyn CodeType>> {
    match inner {
        Type::Int16 | Type::UInt16 => Some(Box::new(Int16ArrayCodeType)),
        Type::Int32 | Type::UInt32 => Some(Box::new(Int32ArrayCodeType)),
        Type::Int64 | Type::UInt64 => Some(Box::new(Int64ArrayCodeType)),
        Type::Float32 => Some(Box::new(Float32ArrayCodeType)),
        Type::Float64 => Some(Box::new(Float64ArrayCodeType)),
        Type::Boolean => Some(Box::new(BooleanArrayCodeType)),
        _ => None,
    }
}

/// Whether `Vec<inner>` renders as a Java primitive array.
pub fn renders_as_primitive_array(inner: &Type) -> bool {
    primitive_array_code_type(inner).is_some()
}

// Primitive array types for sequences of primitives.
// These generate Java primitive arrays (e.g., float[], int[]) instead of List<Boxed>.

macro_rules! impl_primitive_array_code_type {
    ($name:ident, $type_label:literal, $canonical_name:literal) => {
        #[derive(Debug)]
        pub struct $name;

        impl CodeType for $name {
            fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
                $type_label.into()
            }

            fn canonical_name(&self) -> String {
                $canonical_name.into()
            }
        }
    };
}

impl_primitive_array_code_type!(Int16ArrayCodeType, "short[]", "Int16Array");
impl_primitive_array_code_type!(Int32ArrayCodeType, "int[]", "Int32Array");
impl_primitive_array_code_type!(Int64ArrayCodeType, "long[]", "Int64Array");
impl_primitive_array_code_type!(Float32ArrayCodeType, "float[]", "Float32Array");
impl_primitive_array_code_type!(Float64ArrayCodeType, "double[]", "Float64Array");
impl_primitive_array_code_type!(BooleanArrayCodeType, "boolean[]", "BooleanArray");
