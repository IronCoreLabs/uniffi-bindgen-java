package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

final class NamespaceLibrary {
  static synchronized String findLibraryName(String componentName) {
    String libOverride = System.getProperty("uniffi.component." + componentName + ".libraryOverride");
    if (libOverride != null) {
        return libOverride;
    }
    return "{{ config.cdylib_name() }}";
  }

  static SymbolLookup loadLibrary(String componentName) {
    System.loadLibrary(findLibraryName(componentName));
    return SymbolLookup.loaderLookup();
  }

  static void uniffiCheckContractApiVersion() {
    // Get the bindings contract version from our ComponentInterface
    int bindingsContractVersion = {{ ci.uniffi_contract_version() }};
    // Get the scaffolding contract version by calling into the dylib
    int scaffoldingContractVersion = UniffiLib.{{ ci.ffi_uniffi_contract_version().name() }}();
    if (bindingsContractVersion != scaffoldingContractVersion) {
        throw new RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project");
    }
  }

  static void uniffiCheckApiChecksums() {
    {%- for (name, expected_checksum) in ci.iter_checksums() %}
    if (UniffiLib.{{ name }}() != ((short) {{ expected_checksum }})) {
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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

final class {{ callback.name()|ffi_callback_name }} {
    public static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.{% match callback.return_type() %}{%- when Some(return_type) %}of({{ return_type|ffi_value_layout }}{%- when None %}ofVoid({%- endmatch -%}
        {%- for arg in callback.arguments() -%}
        {%- if callback.return_type().is_some() || !loop.first %}, {% endif -%}
        {{ arg.type_().borrow()|ffi_value_layout }}
        {%- endfor -%}
        {%- if callback.has_rust_call_status_arg() -%}
        {%- if callback.return_type().is_some() || callback.arguments().len() != 0 %}, {% endif -%}
        ValueLayout.ADDRESS
        {%- endif -%}
    );

    @FunctionalInterface
    public interface Fn {
        {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct(config, ci) }}{%- when None %}void{%- endmatch %} callback(
            {%- for arg in callback.arguments() -%}
            {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last %},{% endif %}
            {%- endfor -%}
            {%- if callback.has_rust_call_status_arg() -%}{% if callback.arguments().len() != 0 %},{% endif %}
            MemorySegment uniffiCallStatus
            {%- endif -%}
        );
    }

    public static MemorySegment toUpcallStub(Fn fn, Arena arena) {
        try {
            MethodHandle handle = MethodHandles.lookup().findVirtual(
                Fn.class,
                "callback",
                MethodType.methodType(
                    {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct(config, ci) }}.class{%- when None %}void.class{%- endmatch %}
                    {%- for arg in callback.arguments() -%}
                    , {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }}.class
                    {%- endfor -%}
                    {%- if callback.has_rust_call_status_arg() -%}
                    , MemorySegment.class
                    {%- endif -%}
                )
            ).bindTo(fn);
            return Linker.nativeLinker().upcallStub(handle, DESCRIPTOR, arena);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
{%- when FfiDefinition::Struct(ffi_struct) %}
package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
public final class {{ ffi_struct.name()|ffi_struct_name }} {
    public static final StructLayout LAYOUT = MemoryLayout.structLayout(
        {{ ffi_struct|ffi_struct_layout_fields }}
    );

    {%- for field in ffi_struct.fields() %}
    private static final long OFFSET_{{ field.name()|var_name_raw|upper }} = LAYOUT.byteOffset(MemoryLayout.PathElement.groupElement("{{ field.name()|var_name_raw }}"));
    {%- if field.type_().borrow()|ffi_type_is_embedded_struct %}
    private static final long SIZE_{{ field.name()|var_name_raw|upper }} = {{ field.type_().borrow()|ffi_value_layout }}.byteSize();
    {%- endif %}
    {%- endfor %}

    private {{ ffi_struct.name()|ffi_struct_name }}() {}

    public static MemorySegment allocate(Arena arena) {
        return arena.allocate(LAYOUT);
    }

    {%- for field in ffi_struct.fields() %}
    {%- if field.type_().borrow()|ffi_type_is_embedded_struct %}

    public static MemorySegment get{{ field.name()|var_name_raw }}(MemorySegment seg) {
        return seg.asSlice(OFFSET_{{ field.name()|var_name_raw|upper }}, SIZE_{{ field.name()|var_name_raw|upper }});
    }

    public static void set{{ field.name()|var_name_raw }}(MemorySegment seg, MemorySegment value) {
        MemorySegment.copy(value, 0, seg, OFFSET_{{ field.name()|var_name_raw|upper }}, SIZE_{{ field.name()|var_name_raw|upper }});
    }
    {%- else %}

    public static {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} get{{ field.name()|var_name_raw }}(MemorySegment seg) {
        return seg.get({{ field.type_().borrow()|ffi_value_layout_unaligned }}, OFFSET_{{ field.name()|var_name_raw|upper }});
    }

    public static void set{{ field.name()|var_name_raw }}(MemorySegment seg, {{ field.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} value) {
        seg.set({{ field.type_().borrow()|ffi_value_layout_unaligned }}, OFFSET_{{ field.name()|var_name_raw|upper }}, value);
    }
    {%- endif %}
    {%- endfor %}
}
{%- when FfiDefinition::Function(_) %}
{# functions are handled below #}
{%- endmatch %}
{%- endfor %}

package {{ config.package_name() }};

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

// FFM-based native library interface.
// All access is via static methods. No INSTANCE field.
final class UniffiLib {
    static final Linker LINKER = Linker.nativeLinker();
    // Load symbols first so MethodHandle field initializers can reference them.
    static final SymbolLookup SYMBOLS = NamespaceLibrary.loadLibrary("{{ ci.namespace() }}");

    {% if ci.contains_object_types() %}
    // The Cleaner for the whole library
    static final UniffiCleaner CLEANER = UniffiCleaner.create();
    {%- endif %}

    {% for func in ci.iter_ffi_function_definitions() -%}
    private static final MethodHandle MH_{{ func.name() }} = LINKER.downcallHandle(
        SYMBOLS.find("{{ func.name() }}").orElseThrow(),
        {% match func.return_type() %}{% when Some(return_type) %}FunctionDescriptor.of({{ return_type|ffi_value_layout }}{% when None %}FunctionDescriptor.ofVoid({% endmatch -%}
            {%- for arg in func.arguments() -%}
            {%- if func.return_type().is_some() || !loop.first %}, {% endif -%}
            {{ arg.type_().borrow()|ffi_value_layout }}
            {%- endfor -%}
            {%- if func.has_rust_call_status_arg() -%}
            {%- if func.return_type().is_some() || func.arguments().len() != 0 %}, {% endif -%}
            ValueLayout.ADDRESS
            {%- endif -%}
        )
        {%- match func.return_type() %}{% when Some(return_type) %}{% when None %}{% endmatch %}
    );
    static {% match func.return_type() %}{% when Some(return_type) %}{{ return_type.borrow()|ffi_type_name_for_ffi_struct(config, ci) }}{% when None %}void{% endmatch %} {{ func.name() }}(
        {%- match func.return_type() %}{% when Some(return_type) %}{% if return_type|ffi_type_is_struct %}SegmentAllocator _uniffi_allocator{% if func.arguments().len() != 0 || func.has_rust_call_status_arg() %}, {% endif %}{% endif %}{% when None %}{% endmatch -%}
        {%- for arg in func.arguments() -%}
        {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ arg.name()|var_name }}{% if !loop.last || func.has_rust_call_status_arg() %}, {% endif -%}
        {%- endfor -%}
        {%- if func.has_rust_call_status_arg() %}MemorySegment uniffi_out_err{% endif -%}
    ) {
        try {
            {% match func.return_type() %}{% when Some(return_type) %}return {{ return_type|ffi_invoke_exact_cast }}MH_{{ func.name() }}.invokeExact(
                {%- if return_type|ffi_type_is_struct %}_uniffi_allocator{% if func.arguments().len() != 0 || func.has_rust_call_status_arg() %}, {% endif %}{% endif -%}
            {% when None %}MH_{{ func.name() }}.invokeExact({% endmatch -%}
                {%- for arg in func.arguments() -%}
                {{ arg.name()|var_name }}{% if !loop.last || func.has_rust_call_status_arg() %}, {% endif -%}
                {%- endfor -%}
                {%- if func.has_rust_call_status_arg() %}uniffi_out_err{% endif -%}
            );
        } catch (Throwable _ex) {
            throw new AssertionError("Unexpected exception from FFI call", _ex);
        }
    }
    {% endfor %}

    // Verify contract version and checksums after all MethodHandles are initialized.
    static {
        NamespaceLibrary.uniffiCheckContractApiVersion();
        NamespaceLibrary.uniffiCheckApiChecksums();
        {% for init_fn in self.initialization_fns() -%}
        {{ init_fn }};
        {% endfor -%}
    }
}
