package {{ config.package_name() }};

import com.sun.jna.Native;

final class NamespaceLibrary {
  static synchronized String findLibraryName(String componentName) {
    String libOverride = System.getProperty("uniffi.component." + componentName + ".libraryOverride");
    if (libOverride != null) {
        return libOverride;
    }
    return "{{ config.cdylib_name() }}";
  }

  static void uniffiCheckContractApiVersion() {
    // Get the bindings contract version from our ComponentInterface
    int bindingsContractVersion = {{ ci.uniffi_contract_version() }};
    // Get the scaffolding contract version by calling into the dylib
    int scaffoldingContractVersion = IntegrityCheckingUniffiLib.{{ ci.ffi_uniffi_contract_version().name() }}();
    if (bindingsContractVersion != scaffoldingContractVersion) {
        throw new RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project");
    }
  }

{%- if !config.omit_checksums() %}
  static void uniffiCheckApiChecksums() {
    {%- for (name, expected_checksum) in ci.iter_checksums() %}
    if (IntegrityCheckingUniffiLib.{{ name }}() != ((short) {{ expected_checksum }})) {
        throw new RuntimeException("UniFFI API checksum mismatch: try cleaning and rebuilding your project");
    }
    {%- endfor %}
  }
{%- endif %}
}

// Define FFI callback types
{%- for def in ci.ffi_definitions() %}
{%- match def %}
{%- when FfiDefinition::CallbackFunction(callback) %}
package {{ config.package_name() }};

import com.sun.jna.*;
import com.sun.jna.ptr.*;

interface {{ callback.name()|ffi_callback_name }} extends Callback {
    public {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct(config, ci) }}{%- when None %}void{%- endmatch %} callback(
        {%- for arg in callback.arguments() -%}
        {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last %},{% endif %}
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
    public {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ field.name()|var_name }} = {{ field.type_()|ffi_default_value }};
    {%- endfor %}

    // no-arg constructor required so JNA can instantiate and reflect
    public {{ ffi_struct.name()|ffi_struct_name}}() {
        super();
    }

    public {{ ffi_struct.name()|ffi_struct_name }}(
        {%- for field in ffi_struct.fields() %}
        {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ field.name()|var_name }}{% if !loop.last %},{% endif %}
        {%- endfor %}
    ) {
        {%- for field in ffi_struct.fields() %}
        this.{{ field.name()|var_name }} = {{ field.name()|var_name }};
        {%- endfor %}
    }

    public static class UniffiByValue extends {{ ffi_struct.name()|ffi_struct_name }} implements Structure.ByValue {
        public UniffiByValue(
            {%- for field in ffi_struct.fields() %}
            {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ field.name()|var_name }}{% if !loop.last %},{% endif %}
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
{#- functions are handled below #}
{%- endmatch %}
{%- endfor %}

package {{ config.package_name() }};

import com.sun.jna.Native;

// For large crates we prevent `MethodTooLargeException` (see #2340)
// N.B. the name of the exception is very misleading, since it is
// rather `InterfaceTooLargeException`, caused by too many methods
// in the interface for large crates.
//
// By splitting the otherwise huge interface into two parts
// * UniffiLib
// * IntegrityCheckingUniffiLib
// And all checksum methods are put into `IntegrityCheckingUniffiLib`
// we allow for ~2x as many methods in the UniffiLib interface.
//
// Note: above all written when we used JNA's `loadIndirect` etc.
// We now use JNA's "direct mapping" - unclear if same considerations apply exactly.
final class IntegrityCheckingUniffiLib {
    static {
        Native.register(IntegrityCheckingUniffiLib.class, NamespaceLibrary.findLibraryName("{{ ci.namespace() }}"));
        NamespaceLibrary.uniffiCheckContractApiVersion();
{%- if !config.omit_checksums() %}
        NamespaceLibrary.uniffiCheckApiChecksums();
{%- endif %}
    }

    {% for func in ci.iter_ffi_function_integrity_checks() -%}
    native static {% match func.return_type() %}{% when Some with (return_type) %}{{ return_type.borrow()|ffi_type_name_primitive(config, ci) }}{% when None %}void{% endmatch %} {{ func.name() }}({%- call java::arg_list_ffi_decl_primitive(func) %});
    {% endfor %}
}

package {{ config.package_name() }};

import com.sun.jna.Native;
import com.sun.jna.Pointer;

// A JNA Library to expose the extern-C FFI definitions.
// This is an implementation detail which will be called internally by the public API.
final class UniffiLib {
    {% if ci.contains_object_types() %}
    // The Cleaner for the whole library
    static UniffiCleaner CLEANER;
    {%- endif %}

    static {
        Native.register(UniffiLib.class,
            NamespaceLibrary.findLibraryName("{{ ci.namespace() }}"));
        // Force IntegrityCheckingUniffiLib to load first to run integrity checks
        Class<?> ignored = IntegrityCheckingUniffiLib.class;
        {% if ci.contains_object_types() %}
        CLEANER = UniffiCleaner.create();
        {%- endif %}
        {% for init_fn in self.initialization_fns() -%}
        {{ init_fn }}();
        {% endfor -%}
    }

    {% for func in ci.iter_ffi_function_definitions_excluding_integrity_checks() -%}
    native static {% match func.return_type() %}{% when Some with (return_type) %}{{ return_type.borrow()|ffi_type_name_primitive(config, ci) }}{% when None %}void{% endmatch %} {{ func.name() }}({%- call java::arg_list_ffi_decl_primitive(func) %});
    {% endfor %}
}

package {{ config.package_name() }};

/**
 * Ensures the native library is initialized.
 * Call this function to force initialization before using any types from this library.
 */
public final class UniffiInitializer {
    /**
     * Force initialization of the native library.
     * UniffiLib is initialized as classes are used, but we still need to explicitly
     * reference it so initialization across crates works as expected.
     */
    public static void ensureInitialized() {
        // Force IntegrityCheckingUniffiLib class to load (runs integrity checks)
        Class<?> ignored1 = IntegrityCheckingUniffiLib.class;
        // Force UniffiLib class to load (runs initialization functions)
        Class<?> ignored2 = UniffiLib.class;
    }
}
