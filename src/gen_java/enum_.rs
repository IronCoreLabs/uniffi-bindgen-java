/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{CodeType, Config, potentially_add_external_package};
use uniffi_bindgen::interface::{ComponentInterface, DefaultValue, Literal};

#[derive(Debug)]
pub struct EnumCodeType {
    id: String,
}

impl EnumCodeType {
    pub fn new(id: String) -> Self {
        Self { id }
    }
}

impl CodeType for EnumCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        potentially_add_external_package(
            config,
            ci,
            &self.id,
            super::JavaCodeOracle.class_name(ci, &self.id),
        )
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.id)
    }

    fn default(&self, default: &DefaultValue, ci: &ComponentInterface, config: &Config) -> Result<String, askama::Error> {
        match default {
            DefaultValue::Literal(Literal::Enum(v, _)) => Ok(format!(
                "{}.{}",
                self.type_label(ci, config),
                super::JavaCodeOracle.enum_variant_name(v)
            )),
            _ => Err(uniffi_bindgen::to_askama_error(&format!(
                "Invalid default for Enum type: {default:?}"
            ))),
        }
    }

    fn ffi_converter_instance(&self, config: &Config, ci: &ComponentInterface) -> String {
        potentially_add_external_package(
            config,
            ci,
            &self.id,
            format!("{}.INSTANCE", self.ffi_converter_name()),
        )
    }
}
