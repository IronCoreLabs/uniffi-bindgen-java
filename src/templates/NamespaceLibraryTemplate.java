package {{ config.package_name() }};

final class NamespaceLibrary {
    static synchronized String findLibraryName(String componentName) {
        String libOverride = System.getProperty("uniffi.component." + componentName + ".libraryOverride");
        if (libOverride != null) {
            return libOverride;
        }
        return "{{ config.cdylib_name() }}";
    }

    static java.lang.foreign.SymbolLookup loadLibrary() {
        String name = findLibraryName("{{ ci.namespace() }}");
        if (name.startsWith("/") // Unix absolute path
                || name.startsWith("\\\\") // Windows UNC path
                || (name.length() > 2 && name.charAt(1) == ':')) // Windows drive path (e.g. C:\)
        {
            System.load(name);
        } else {
            System.loadLibrary(name);
        }
        return java.lang.foreign.SymbolLookup.loaderLookup();
    }

    static void uniffiCheckContractApiVersion() {
        int bindingsContractVersion = {{ ci.uniffi_contract_version() }};
        int scaffoldingContractVersion = UniffiLib.{{ ci.ffi_uniffi_contract_version().name() }}();
        if (bindingsContractVersion != scaffoldingContractVersion) {
            throw new RuntimeException("UniFFI contract version mismatch: try cleaning and rebuilding your project");
        }
    }

{%- if !config.omit_checksums() %}
    static void uniffiCheckApiChecksums() {
    {%- for (name, expected_checksum) in ci.iter_checksums() %}
        if (UniffiLib.{{ name }}() != ((short) {{ expected_checksum }})) {
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

public final class {{ callback.name()|ffi_callback_name }} {
    public static final java.lang.foreign.FunctionDescriptor DESCRIPTOR = java.lang.foreign.FunctionDescriptor.of{% match callback.return_type() %}{%- when Some(return_type) %}({{ return_type|ffi_value_layout }}{%- for arg in callback.arguments() %}, {{ arg.type_().borrow()|ffi_value_layout }}{% endfor %}{%- if callback.has_rust_call_status_arg() %}, java.lang.foreign.ValueLayout.ADDRESS{% endif %}){%- when None %}Void({% for arg in callback.arguments() %}{{ arg.type_().borrow()|ffi_value_layout }}{% if !loop.last %}, {% endif %}{% endfor %}{%- if callback.has_rust_call_status_arg() %}{% if callback.arguments().len() != 0 %}, {% endif %}java.lang.foreign.ValueLayout.ADDRESS{% endif %}){%- endmatch %};

    @FunctionalInterface
    public interface Fn {
        {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name(config, ci) }}{%- when None %}void{%- endmatch %} callback(
            {%- for arg in callback.arguments() -%}
            {{ arg.type_().borrow()|ffi_type_name(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last %},{% endif %}
            {%- endfor -%}
            {%- if callback.has_rust_call_status_arg() -%}{% if callback.arguments().len() != 0 %},{% endif %}
            java.lang.foreign.MemorySegment uniffiCallStatus
            {%- endif -%}
        );
    }

    public static java.lang.foreign.MemorySegment toUpcallStub(Fn fn, java.lang.foreign.Arena arena) {
        try {
            java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findVirtual(Fn.class, "callback", java.lang.invoke.MethodType.methodType(
                    {% match callback.return_type() %}{%- when Some(return_type) %}{{ return_type|ffi_type_name(config, ci) }}.class{%- when None %}void.class{%- endmatch %},
                    {%- for arg in callback.arguments() %}
                    {{ arg.type_().borrow()|ffi_type_name(config, ci) }}.class{% if !loop.last %},{% endif %}
                    {%- endfor %}
                    {%- if callback.has_rust_call_status_arg() %}
                    {% if callback.arguments().len() != 0 %},{% endif %}java.lang.foreign.MemorySegment.class
                    {%- endif %}
                ))
                .bindTo(fn);
            return java.lang.foreign.Linker.nativeLinker().upcallStub(handle, DESCRIPTOR, arena);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("Failed to create upcall stub", e);
        }
    }
}
{%- when FfiDefinition::Struct(ffi_struct) %}
package {{ config.package_name() }};

public final class {{ ffi_struct.name()|ffi_struct_name }} {
    public static final java.lang.foreign.StructLayout LAYOUT = java.lang.foreign.MemoryLayout.structLayout(
        {{ ffi_struct|ffi_struct_layout_body }}
    );
    {%- for field in ffi_struct.fields() %}

    private static final long OFFSET_{{ field.name()|var_name_raw|fn_name }} = LAYOUT.byteOffset(java.lang.foreign.MemoryLayout.PathElement.groupElement("{{ field.name()|var_name_raw }}"));
    {%- endfor %}

    private {{ ffi_struct.name()|ffi_struct_name }}() {}
    {%- for field in ffi_struct.fields() %}

    {%- if field.type_().borrow()|ffi_type_is_embedded_struct %}
    public static java.lang.foreign.MemorySegment get{{ field.name()|var_name_raw }}(java.lang.foreign.MemorySegment seg) {
        return seg.asSlice(OFFSET_{{ field.name()|var_name_raw|fn_name }}, {{ field.type_().borrow()|ffi_struct_type_name }}.LAYOUT.byteSize());
    }

    public static void set{{ field.name()|var_name_raw }}(java.lang.foreign.MemorySegment seg, java.lang.foreign.MemorySegment value) {
        java.lang.foreign.MemorySegment.copy(value, 0, seg, OFFSET_{{ field.name()|var_name_raw|fn_name }}, {{ field.type_().borrow()|ffi_struct_type_name }}.LAYOUT.byteSize());
    }
    {%- else %}
    public static {{ field.type_().borrow()|ffi_type_name(config, ci) }} get{{ field.name()|var_name_raw }}(java.lang.foreign.MemorySegment seg) {
        return {{ field.type_().borrow()|ffi_invoke_exact_cast }}seg.get({{ field.type_().borrow()|ffi_value_layout_unaligned }}, OFFSET_{{ field.name()|var_name_raw|fn_name }});
    }

    public static void set{{ field.name()|var_name_raw }}(java.lang.foreign.MemorySegment seg, {{ field.type_().borrow()|ffi_type_name(config, ci) }} value) {
        seg.set({{ field.type_().borrow()|ffi_value_layout_unaligned }}, OFFSET_{{ field.name()|var_name_raw|fn_name }}, value);
    }
    {%- endif %}
    {%- endfor %}

    /**
     * Allocate a new instance in the given arena.
     */
    public static java.lang.foreign.MemorySegment allocate(java.lang.foreign.SegmentAllocator allocator) {
        java.lang.foreign.MemorySegment seg = allocator.allocate(LAYOUT);
        seg.fill((byte) 0);
        return seg;
    }
}
{%- when FfiDefinition::Function(_) %}
{#- functions are handled below #}
{%- endmatch %}
{%- endfor %}

package {{ config.package_name() }};

// FFM-based library binding. Each FFI function gets a MethodHandle and a wrapper method.
final class UniffiLib {
    private static final java.lang.foreign.Linker LINKER = java.lang.foreign.Linker.nativeLinker();
    private static final java.lang.foreign.SymbolLookup SYMBOLS;

    {% if ci.contains_object_types() %}
    // The Cleaner for the whole library
    static UniffiCleaner CLEANER;
    {%- endif %}

    static {
        SYMBOLS = NamespaceLibrary.loadLibrary();
    }

    private static java.lang.invoke.MethodHandle findDowncallHandle(String name, java.lang.foreign.FunctionDescriptor descriptor) {
        return SYMBOLS.find(name)
            .map(s -> LINKER.downcallHandle(s, descriptor))
            .orElseThrow(() -> new RuntimeException("Missing FFI symbol: " + name));
    }

    {% for func in ci.iter_ffi_function_definitions() -%}
    // {{ func.name() }}
    {%- match func.return_type() %}
    {%- when Some(return_type) %}
    {%- if return_type|ffi_type_is_struct %}
    private static final java.lang.invoke.MethodHandle MH_{{ func.name() }} = findDowncallHandle("{{ func.name() }}", java.lang.foreign.FunctionDescriptor.of({{ return_type|ffi_value_layout }}{% for arg in func.arguments() %}, {{ arg.type_().borrow()|ffi_value_layout }}{% endfor %}{% if func.has_rust_call_status_arg() %}, java.lang.foreign.ValueLayout.ADDRESS{% endif %}));

    static java.lang.foreign.MemorySegment {{ func.name() }}(java.lang.foreign.SegmentAllocator _allocator{% for arg in func.arguments() %}, {{ arg.type_().borrow()|ffi_type_name(config, ci) }} {{ arg.name()|var_name }}{% endfor %}{% if func.has_rust_call_status_arg() %}, java.lang.foreign.MemorySegment uniffiOutErr{% endif %}) {
        try {
            return (java.lang.foreign.MemorySegment) MH_{{ func.name() }}.invokeExact(_allocator{% for arg in func.arguments() %}, {{ arg.name()|var_name }}{% endfor %}{% if func.has_rust_call_status_arg() %}, uniffiOutErr{% endif %});
        } catch (Throwable _ex) { throw new AssertionError("invokeExact failed", _ex); }
    }
    {%- else %}
    private static final java.lang.invoke.MethodHandle MH_{{ func.name() }} = findDowncallHandle("{{ func.name() }}", java.lang.foreign.FunctionDescriptor.of({{ return_type|ffi_value_layout }}{% for arg in func.arguments() %}, {{ arg.type_().borrow()|ffi_value_layout }}{% endfor %}{% if func.has_rust_call_status_arg() %}, java.lang.foreign.ValueLayout.ADDRESS{% endif %}));

    static {{ return_type|ffi_type_name(config, ci) }} {{ func.name() }}({% for arg in func.arguments() %}{{ arg.type_().borrow()|ffi_type_name(config, ci) }} {{ arg.name()|var_name }}{% if !loop.last %}, {% endif %}{% endfor %}{% if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}java.lang.foreign.MemorySegment uniffiOutErr{% endif %}) {
        try {
            return {{ return_type|ffi_invoke_exact_cast }}MH_{{ func.name() }}.invokeExact({% for arg in func.arguments() %}{{ arg.name()|var_name }}{% if !loop.last %}, {% endif %}{% endfor %}{% if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}uniffiOutErr{% endif %});
        } catch (Throwable _ex) { throw new AssertionError("invokeExact failed", _ex); }
    }
    {%- endif %}
    {%- when None %}
    private static final java.lang.invoke.MethodHandle MH_{{ func.name() }} = findDowncallHandle("{{ func.name() }}", java.lang.foreign.FunctionDescriptor.ofVoid({% for arg in func.arguments() %}{{ arg.type_().borrow()|ffi_value_layout }}{% if !loop.last %}, {% endif %}{% endfor %}{% if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}java.lang.foreign.ValueLayout.ADDRESS{% endif %}));

    static void {{ func.name() }}({% for arg in func.arguments() %}{{ arg.type_().borrow()|ffi_type_name(config, ci) }} {{ arg.name()|var_name }}{% if !loop.last %}, {% endif %}{% endfor %}{% if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}java.lang.foreign.MemorySegment uniffiOutErr{% endif %}) {
        try {
            MH_{{ func.name() }}.invokeExact({% for arg in func.arguments() %}{{ arg.name()|var_name }}{% if !loop.last %}, {% endif %}{% endfor %}{% if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}uniffiOutErr{% endif %});
        } catch (Throwable _ex) { throw new AssertionError("invokeExact failed", _ex); }
    }
    {%- endmatch %}

    {% endfor %}

    // Integrity checks and initialization must happen after all MethodHandle fields
    // are initialized (static fields are initialized in textual order in Java).
    static {
        NamespaceLibrary.uniffiCheckContractApiVersion();
{%- if !config.omit_checksums() %}
        NamespaceLibrary.uniffiCheckApiChecksums();
{%- endif %}
        {% if ci.contains_object_types() %}
        CLEANER = UniffiCleaner.create();
        {%- endif %}
        {% for init_fn in self.initialization_fns() -%}
        {{ init_fn }}();
        {% endfor -%}
    }
}

package {{ config.package_name() }};

/**
 * Ensures the native library is initialized.
 * Call this function to force initialization before using any types from this library.
 */
public final class UniffiInitializer {
    /**
     * Force initialization of the native library.
     */
    public static void ensureInitialized() {
        // Force UniffiLib class to load (runs integrity checks and initialization functions)
        Class<?> ignored = UniffiLib.class;
    }
}
