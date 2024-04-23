package {{ config.package_name() }};

import com.sun.jna.Library;
import com.sun.jna.Native;

final class NamespaceLibrary {
  static synchronized String findLibraryName(String componentName) {
    String libOverride = System.getProperty("uniffi.component." + componentName + ".libraryOverride");
    if (libOverride != null) {
        return libOverride;
    }
    return "{{ config.cdylib_name() }}";
  }

  static <Lib extends Library> Lib loadIndirect(String componentName) {
    return Native.load(findLibraryName(componentName), (Class<Lib>) Lib.class);
  }

  void uniffiCheckContractApiVersion(UniffiLib lib) {
    // Get the bindings contract version from our ComponentInterface
    int bindingsContractVersion = {{ ci.uniffi_contract_version() }};
    // Get the scaffolding contract version by calling the into the dylib
    int scaffoldingContractVersion = lib.{{ ci.ffi_uniffi_contract_version().name() }}();
    if (bindingsContractVersion != scaffoldingContractVersion) {
        throw new RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project");
    }
  }

  void uniffiCheckApiChecksums(UniffiLib lib) {
    {%- for (name, expected_checksum) in ci.iter_checksums() %}
    if (lib.{{ name }}() != ((short) {{ expected_checksum }})) {
        throw RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project");
    }
    {%- endfor %}
  }
}

// Define FFI callback types
{%- for def in ci.ffi_definitions() %}
package {{ config.package_name() }};

{%- match def %}
{%- when FfiDefinition::CallbackFunction(callback) %}
import com.sun.jna.Callback;

interface {{ callback.name()|ffi_callback_name }} extends Callback {
    {%- match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name_by_value }}{%- when None %}void{%- endmatch %} callback(
        {%- for arg in callback.arguments() -%}
        {{ arg.type_().borrow()|ffi_type_name_by_value }} {{ arg.name().borrow()|var_name }},
        {%- endfor -%}
        {%- if callback.has_rust_call_status_arg() -%}
        uniffiCallStatus: UniffiRustCallStatus,
        {%- endif -%}
    )
}
{%- when FfiDefinition::Struct(ffi_struct) %}
import com.sun.jna.Structure;

@Structure.FieldOrder({% for field in ffi_struct.fields() %}"{{ field.name()|var_name }}"{% if !loop.last %}, {% endif %}{% endfor %})
class {{ ffi_struct.name()|ffi_struct_name }}(
    {%- for field in ffi_struct.fields() %}
    {{ field.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ field.name()|var_name }} = {{ field.type_()|ffi_default_value }},
    {%- endfor %}
) extends Structure {
    class UniffiByValue(
        {%- for field in ffi_struct.fields() %}
        {{ field.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ field.name()|var_name }} = {{ field.type_()|ffi_default_value }},
        {%- endfor %}
    ) extends {{ ffi_struct.name()|ffi_struct_name }}({%- for field in ffi_struct.fields() %}{{ field.name()|var_name }}, {%- endfor %}) implements Structure.ByValue {}

   void uniffiSetValue(other: {{ ffi_struct.name()|ffi_struct_name }}) {
        {%- for field in ffi_struct.fields() %}
        {{ field.name()|var_name }} = other.{{ field.name()|var_name }};
        {%- endfor %}
    }

}
{%- when FfiDefinition::Function(_) %}
{# functions are handled below #}
{%- endmatch %}
{%- endfor %}

package {{ config.package_name() }};

import com.sun.jna.Library;

// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.
interface UniffiLib extends Library {
    UniffiLib INSTANCE = NamespaceLibrary.loadIndirect("{{ ci.namespace() }}");

    static {
        NamespaceLibrary.uniffiCheckContractApiVersion(INSTANCE);
        NamespaceLibrary.uniffiCheckApiChecksums(INSTANCE);
        {% for fn in self.initialization_fns() -%}
        {{ fn }}(INSTANCE)
        {% endfor -%}
    }
    {% if ci.contains_object_types() %}
    // The Cleaner for the whole library
    static UniffiCleaner CLEANER = UniffiCleaner.create();
    {%- endif %}
    
    {% for func in ci.iter_ffi_function_definitions() -%}
    {% match func.return_type() %}{% when Some with (return_type) %}{{ return_type.borrow()|ffi_type_name_by_value }}{% when None %}void{% endmatch %} {{ func.name() }}({%- call java::arg_list_ffi_decl(func) %});
    {% endfor %}
}
