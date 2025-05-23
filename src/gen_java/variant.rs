/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

use super::{AsCodeType, CodeType, Config, JavaCodeOracle, potentially_add_external_package};
use uniffi_bindgen::interface::{ComponentInterface, Variant};

#[derive(Debug)]
pub(super) struct VariantCodeType {
    pub v: Variant,
}

impl CodeType for VariantCodeType {
    fn type_label(&self, ci: &ComponentInterface, config: &Config) -> String {
        potentially_add_external_package(
            config,
            ci,
            self.v.name(),
            JavaCodeOracle.class_name(ci, self.v.name()),
        )
    }

    fn canonical_name(&self) -> String {
        self.v.name().to_string()
    }
}

impl AsCodeType for Variant {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        Box::new(VariantCodeType { v: self.clone() })
    }
}

impl AsCodeType for &Variant {
    fn as_codetype(&self) -> Box<dyn CodeType> {
        Box::new(VariantCodeType { v: (*self).clone() })
    }
}
