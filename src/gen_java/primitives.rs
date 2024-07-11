use super::CodeType;
use paste::paste;
use uniffi_bindgen::backend::Literal;
use uniffi_bindgen::interface::{ComponentInterface, Radix, Type};

fn render_literal(literal: &Literal, _ci: &ComponentInterface) -> String {
    fn typed_number(type_: &Type, num_str: String) -> String {
        let unwrapped_type = match type_ {
            Type::Optional { inner_type } => inner_type,
            t => t,
        };
        match unwrapped_type {
            // Bytes, Shorts and Ints can all be inferred from the type.
            Type::Int8 | Type::Int16 | Type::Int32 => num_str,
            Type::Int64 => format!("{num_str}L"),

            // TODO(murph): Java has pretty hacky unsigned support, see https://docs.oracle.com/javase/8/docs/api/java/lang/Long.html (search for unsigned)
            // and https://docs.oracle.com/javase/8/docs/api/java/lang/Integer.html search for unsigned. Have to see how this affects generated code, if it's possible to use these things
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
    ($T:ty, $class_name:literal) => {
        paste! {
            #[derive(Debug)]
            pub struct $T;

            impl CodeType for $T  {
                fn type_label(&self, _ci: &ComponentInterface) -> String {
                    format!("{}", $class_name)
                }

                fn canonical_name(&self) -> String {
                    $class_name.into()
                }

                fn literal(&self, literal: &Literal, ci: &ComponentInterface) -> String {
                    render_literal(&literal, ci)
                }
            }
        }
    };
}

#[derive(Debug)]
pub struct BytesCodeType;
impl CodeType for BytesCodeType {
    fn type_label(&self, _ci: &ComponentInterface) -> String {
        "byte[]".to_string()
    }

    fn canonical_name(&self) -> String {
        "ByteArray".to_string()
    }

    fn literal(&self, literal: &Literal, ci: &ComponentInterface) -> String {
        render_literal(&literal, ci)
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
