use super::{CodeType, Config};
use paste::paste;
use uniffi_bindgen::interface::ComponentInterface;

macro_rules! impl_code_type_for_primitive {
    ($T:ty, $type_label:literal, $canonical_name:literal, $primitive_label:literal) => {
        paste! {
            #[derive(Debug)]
            pub struct $T;

            impl CodeType for $T  {
                fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
                    $type_label.into()
                }

                fn type_label_primitive(&self) -> Option<String> {
                    Some($primitive_label.into())
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

#[derive(Debug)]
pub struct StringCodeType;
impl CodeType for StringCodeType {
    fn type_label(&self, _ci: &ComponentInterface, _config: &Config) -> String {
        "java.lang.String".to_string()
    }

    fn canonical_name(&self) -> String {
        "String".to_string()
    }
}

impl_code_type_for_primitive!(BooleanCodeType, "java.lang.Boolean", "Boolean", "boolean");
impl_code_type_for_primitive!(Int8CodeType, "java.lang.Byte", "Byte", "byte");
impl_code_type_for_primitive!(Int16CodeType, "java.lang.Short", "Short", "short");
impl_code_type_for_primitive!(Int32CodeType, "java.lang.Integer", "Integer", "int");
impl_code_type_for_primitive!(Int64CodeType, "java.lang.Long", "Long", "long");
impl_code_type_for_primitive!(Float32CodeType, "java.lang.Float", "Float", "float");
impl_code_type_for_primitive!(Float64CodeType, "java.lang.Double", "Double", "double");
