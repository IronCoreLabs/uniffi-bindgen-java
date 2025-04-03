/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{potentially_add_external_package, CodeType, Config};
use crate::ComponentInterface;

#[derive(Debug)]
pub struct CustomCodeType {
    name: String,
}

impl CustomCodeType {
    pub fn new(name: String) -> Self {
        CustomCodeType { name }
    }
}

impl CodeType for CustomCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        potentially_add_external_package(
            config,
            ci,
            &self.name,
            super::JavaCodeOracle.class_name(ci, &self.name),
        )
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.name)
    }

    fn ffi_converter_instance(&self, config: &Config, ci: &ComponentInterface) -> String {
        potentially_add_external_package(
            config,
            ci,
            &self.name,
            format!("{}.INSTANCE", self.ffi_converter_name()),
        )
    }
}
