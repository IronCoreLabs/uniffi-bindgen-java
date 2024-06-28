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

  static <Lib extends Library> Lib loadIndirect(String componentName, Class<Lib> clazz) {
    return Native.load(findLibraryName(componentName), clazz);
  }

  static void uniffiCheckContractApiVersion(UniffiLib lib) {
    // Get the bindings contract version from our ComponentInterface
    int bindingsContractVersion = {{ ci.uniffi_contract_version() }};
    // Get the scaffolding contract version by calling the into the dylib
    int scaffoldingContractVersion = lib.{{ ci.ffi_uniffi_contract_version().name() }}();
    if (bindingsContractVersion != scaffoldingContractVersion) {
        throw new RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project");
    }
  }

  static void uniffiCheckApiChecksums(UniffiLib lib) {
    {%- for (name, expected_checksum) in ci.iter_checksums() %}
    if (lib.{{ name }}() != ((short) {{ expected_checksum }})) {
        throw new RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project");
    }
    {%- endfor %}
  }
}

// Define FFI callback types
{%- for def in ci.ffi_definitions() %}
{%- match def %}
{%- when FfiDefinition::CallbackFunction(callback) %}
package {{ config.package_name() }};

import com.sun.jna.Callback;

interface {{ callback.name()|ffi_callback_name }} extends Callback {
    public {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct }}{%- when None %}void{%- endmatch %} callback(
        {%- for arg in callback.arguments() -%}
        {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ arg.name().borrow()|var_name }}{% if !loop.last %},{% endif %}
        {%- endfor -%}
        {%- if callback.has_rust_call_status_arg() -%}{% if callback.arguments().len() != 0 %},{% endif %}
        UniffiRustCallStatus uniffiCallStatus
        {%- endif -%}
    );
}
{%- when FfiDefinition::Struct(ffi_struct) %}
package {{ config.package_name() }};

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

@Structure.FieldOrder({ {% for field in ffi_struct.fields() %}"{{ field.name()|var_name_raw }}"{% if !loop.last %}, {% endif %}{% endfor %} })
public class {{ ffi_struct.name()|ffi_struct_name }} extends Structure {
    {%- for field in ffi_struct.fields() %}
    public {{ field.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ field.name()|var_name }} = {{ field.type_()|ffi_default_value }};
    {%- endfor %}

    // no-arg constructor required so JNA can instantiate and reflect
    public {{ ffi_struct.name()|ffi_struct_name}}() {
        super();
    }
    
    public {{ ffi_struct.name()|ffi_struct_name }}(
        {%- for field in ffi_struct.fields() %}
        {{ field.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ field.name()|var_name }}{% if !loop.last %},{% endif %}
        {%- endfor %}
    ) {
        {%- for field in ffi_struct.fields() %}
        this.{{ field.name()|var_name }} = {{ field.name()|var_name }};
        {%- endfor %}
    }

    public static class UniffiByValue extends {{ ffi_struct.name()|ffi_struct_name }} implements Structure.ByValue {
        public UniffiByValue(
            {%- for field in ffi_struct.fields() %}
            {{ field.type_().borrow()|ffi_type_name_for_ffi_struct }} {{ field.name()|var_name }}{% if !loop.last %},{% endif %}
            {%- endfor %}
        ) {
            super({%- for field in ffi_struct.fields() -%}
                {{ field.name()|var_name }}{% if !loop.last %},{% endif %}        
            {% endfor %});
        }
    }

    void uniffiSetValue({{ ffi_struct.name()|ffi_struct_name }} other) {
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
import com.sun.jna.Pointer;

// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.
interface UniffiLib extends Library {
    UniffiLib INSTANCE = UniffiLibInitializer.load();

    {% if ci.contains_object_types() %}
    // The Cleaner for the whole library
    static UniffiCleaner CLEANER = UniffiCleaner.create();
    {%- endif %}
    
    {% for func in ci.iter_ffi_function_definitions() -%}
    {% match func.return_type() %}{% when Some with (return_type) %}{{ return_type.borrow()|ffi_type_name_by_value }}{% when None %}void{% endmatch %} {{ func.name() }}({%- call java::arg_list_ffi_decl(func) %});
    {% endfor %}
}

package {{ config.package_name() }};

// Java doesn't allow for static init blocks in an interface outside of a static property with a default.
// To get around that and make sure that when the UniffiLib interface loads it has an initialized library
// we call this class. The init code won't be called until a function on this interface is called unfortunately.
final class UniffiLibInitializer {
    static UniffiLib load() {
        UniffiLib instance = NamespaceLibrary.loadIndirect("{{ ci.namespace() }}", UniffiLib.class);
        NamespaceLibrary.uniffiCheckContractApiVersion(instance);
        NamespaceLibrary.uniffiCheckApiChecksums(instance);
        {% for fn in self.initialization_fns() -%}
        {{ fn }}(instance);
        {% endfor -%}
        return instance;
    }
}
