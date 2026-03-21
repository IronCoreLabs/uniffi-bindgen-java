use super::{CodeType, Config};
use paste::paste;
use uniffi_bindgen::interface::ComponentInterface;

macro_rules! impl_code_type_for_primitive {
    ($T:ty, $type_label:literal, $canonical_name:literal) => {
        paste! {
            #[derive(Debug)]
            pub struct $T;

            impl CodeType for $T  {
                fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
                    $type_label.into()
                }

                fn canonical_name(&self) -> String {
                    $canonical_name.into()
                }
            }
        }
    };
}

#[derive(Debug)]
pub struct BytesCodeType;
impl CodeType for BytesCodeType {
    fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
        "byte[]".to_string()
    }

    fn canonical_name(&self) -> String {
        "ByteArray".to_string()
    }
}

impl_code_type_for_primitive!(BooleanCodeType, "java.lang.Boolean", "Boolean");
impl_code_type_for_primitive!(StringCodeType, "java.lang.String", "String");
impl_code_type_for_primitive!(Int8CodeType, "java.lang.Byte", "Byte");
impl_code_type_for_primitive!(Int16CodeType, "java.lang.Short", "Short");
impl_code_type_for_primitive!(Int32CodeType, "java.lang.Integer", "Integer");
impl_code_type_for_primitive!(Int64CodeType, "java.lang.Long", "Long");
impl_code_type_for_primitive!(Float32CodeType, "java.lang.Float", "Float");
impl_code_type_for_primitive!(Float64CodeType, "java.lang.Double", "Double");
