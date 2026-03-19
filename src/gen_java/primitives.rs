use super::{CodeType, Config};
use anyhow::Result;
use paste::paste;
use uniffi_bindgen::interface::{ComponentInterface, DefaultValue, Literal};
use uniffi_meta::{Radix, Type};

fn render_literal(literal: &Literal, _ci: &ComponentInterface, _config: &Config) -> String {
    fn typed_number(type_: &Type, num_str: String) -> String {
        let unwrapped_type = match type_ {
            Type::Optional { inner_type } => inner_type,
            t => t,
        };
        match unwrapped_type {
            // Bytes, Shorts and Ints can all be inferred from the type.
            Type::Int8 | Type::Int16 | Type::Int32 => num_str,
            Type::Int64 => format!("{num_str}L"),

            // Java has pretty hacky unsigned support, see https://docs.oracle.com/javase/8/docs/api/java/lang/Long.html (search for unsigned)
            // and https://docs.oracle.com/javase/8/docs/api/java/lang/Integer.html search for unsigned.
            Type::UInt8 | Type::UInt16 | Type::UInt32 => {
                format!("Integer.parseUnsignedInt({num_str})")
            }
            Type::UInt64 => format!("Long.parseUnsignedLong({num_str})"),

            Type::Float32 => format!("{num_str}f"),
            Type::Float64 => num_str,
            _ => panic!("Unexpected literal: {num_str} for type: {type_:?}"),
        }
    }

    match literal {
        Literal::Boolean(v) => format!("{v}"),
        Literal::String(s) => format!("\"{s}\""),
        Literal::Int(i, radix, type_) => typed_number(
            type_,
            match radix {
                Radix::Octal => format!("{i:#x}"),
                Radix::Decimal => format!("{i}"),
                Radix::Hexadecimal => format!("{i:#x}"),
            },
        ),
        Literal::UInt(i, radix, type_) => typed_number(
            type_,
            match radix {
                Radix::Octal => format!("{i:#x}"),
                Radix::Decimal => format!("{i}"),
                Radix::Hexadecimal => format!("{i:#x}"),
            },
        ),
        Literal::Float(string, type_) => typed_number(type_, string.clone()),

        _ => unreachable!("Literal"),
    }
}

macro_rules! impl_code_type_for_primitive {
    ($T:ty, $class_name:literal, $default:literal) => {
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

                fn default(&self, default: &DefaultValue, ci: &ComponentInterface, config: &Config) -> Result<String, askama::Error> {
                    match default {
                        DefaultValue::Default => Ok($default.into()),
                        DefaultValue::Literal(literal) => Ok(render_literal(literal, ci, config)),
                    }
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

    fn default(&self, default: &DefaultValue, ci: &ComponentInterface, config: &Config) -> Result<String, askama::Error> {
        match default {
            DefaultValue::Default => Ok("new byte[]{}".into()),
            DefaultValue::Literal(literal) => Ok(render_literal(literal, ci, config)),
        }
    }
}

impl_code_type_for_primitive!(BooleanCodeType, "Boolean", "false");
impl_code_type_for_primitive!(StringCodeType, "String", "\"\"");
impl_code_type_for_primitive!(Int8CodeType, "Byte", "(byte)0");
impl_code_type_for_primitive!(Int16CodeType, "Short", "(short)0");
impl_code_type_for_primitive!(Int32CodeType, "Integer", "0");
impl_code_type_for_primitive!(Int64CodeType, "Long", "0L");
impl_code_type_for_primitive!(Float32CodeType, "Float", "0.0f");
impl_code_type_for_primitive!(Float64CodeType, "Double", "0.0");
