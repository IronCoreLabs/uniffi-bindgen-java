/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::CodeType;
use crate::ComponentInterface;

#[derive(Debug)]
pub struct ExternalCodeType {
    name: String,
    module_path: String,
    namespace: String,
}

impl ExternalCodeType {
    pub fn new(name: String, module_path: String, namespace: String) -> Self {
        Self {
            name,
            module_path,
            namespace,
        }
    }
}

impl CodeType for ExternalCodeType {
    fn type_label(&self, ci: &ComponentInterface) -> String {
        // TODO(murph): need config here to see if theres a specific set name
        // not sure how to get it, or pass just that info down
        format!(
            "uniffi.{}.{}",
            // TODO
            //external_type_package_name(self.module_path, self.namespace),
            // self.module_path,
            self.namespace,
            super::JavaCodeOracle.class_name(ci, &self.name)
        )
    }

    // Override to make references to external FfiConverters fully qualified always.
    fn ffi_converter_instance(&self) -> String {
        format!(
            "uniffi.{}.{}.INSTANCE",
            self.namespace,
            self.ffi_converter_name()
        )
    }

    fn canonical_name(&self) -> String {
        format!("Type{}", self.name)
    }
}
