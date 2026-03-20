use super::{CodeType, Config};
use paste::paste;
use uniffi_bindgen::interface::ComponentInterface;

macro_rules! impl_code_type_for_primitive {
    ($T:ty, $class_name:literal) => {
        paste! {
            #[derive(Debug)]
            pub struct $T;

            impl CodeType for $T  {
                fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
                    format!("{}", $class_name)
                }

                fn canonical_name(&self) -> String {
                    $class_name.into()
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

impl_code_type_for_primitive!(BooleanCodeType, "Boolean");
impl_code_type_for_primitive!(StringCodeType, "String");
impl_code_type_for_primitive!(Int8CodeType, "Byte");
impl_code_type_for_primitive!(Int16CodeType, "Short");
impl_code_type_for_primitive!(Int32CodeType, "Integer");
impl_code_type_for_primitive!(Int64CodeType, "Long");
impl_code_type_for_primitive!(Float32CodeType, "Float");
impl_code_type_for_primitive!(Float64CodeType, "Double");
