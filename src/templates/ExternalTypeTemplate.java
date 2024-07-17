{%- let package_name=self.external_type_package_name(module_path, namespace) %}
{%- let fully_qualified_type_name = "{}.{}"|format(package_name, name|class_name(ci)) %}
{%- let fully_qualified_ffi_converter_name = "{}.FfiConverterType{}"|format(package_name, name) %}
{%- let fully_qualified_rustbuffer_name = "{}.RustBuffer"|format(package_name) %}

// TODO(murph): need to add add_import back in for this to work, and load it in possibly every package?
{{- self.add_import(fully_qualified_type_name) }}
{{- self.add_import(fully_qualified_ffi_converter_name) }}
// TODO(murph): this import can't work in Java. May need some workaround, doing a qualified import for now
{{ self.add_import(fully_qualified_rustbuffer_name) }}
