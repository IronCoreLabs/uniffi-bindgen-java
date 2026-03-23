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
        super::JavaCodeOracle
            .find(self.inner())
            .type_label(ci, config)
            .to_string()
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
            super::JavaCodeOracle
                .find(self.key())
                .type_label(ci, config),
            super::JavaCodeOracle
                .find(self.value())
                .type_label(ci, config),
        )
    }

    fn canonical_name(&self) -> String {
        format!(
            "Map{}{}",
            self.key().as_codetype().canonical_name(),
            self.value().as_codetype().canonical_name(),
        )
    }
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
