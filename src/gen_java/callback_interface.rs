/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{CodeType, Config, potentially_add_external_package};
use crate::ComponentInterface;

#[derive(Debug)]
pub struct CallbackInterfaceCodeType {
    id: String,
}

impl CallbackInterfaceCodeType {
    pub fn new(id: String) -> Self {
        Self { id }
    }
}

impl CodeType for CallbackInterfaceCodeType {
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

    fn initialization_fn(&self) -> Option<String> {
        Some(format!(
            "UniffiCallbackInterface{}.INSTANCE.register",
            self.id
        ))
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
