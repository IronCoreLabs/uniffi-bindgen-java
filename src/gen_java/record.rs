/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{CodeType, Config, potentially_add_external_package};
use uniffi_bindgen::ComponentInterface;

#[derive(Debug)]
pub struct RecordCodeType {
    id: String,
}

impl RecordCodeType {
    pub fn new(id: String) -> Self {
        Self { id }
    }
}

impl CodeType for RecordCodeType {
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

    fn ffi_converter_instance(&self, config: &Config, ci: &ComponentInterface) -> String {
        potentially_add_external_package(
            config,
            ci,
            &self.id,
            format!("{}.INSTANCE", self.ffi_converter_name()),
        )
    }
}
