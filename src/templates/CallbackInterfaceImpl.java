package {{ config.package_name() }};

{%- let trait_impl=format!("UniffiCallbackInterface{}", self.name) %}

// Put the implementation in an object so we don't pollute the top-level namespace
public class {{ trait_impl }} {
    public static final {{ trait_impl }} INSTANCE = new {{ trait_impl }}();
    java.lang.foreign.MemorySegment vtable;

    {{ trait_impl }}() {
        // Use Arena.global() for vtable and upcall stubs - they live for the program lifetime.
        // Arena.ofAuto() stubs can be GC'd since storing an address in a struct doesn't
        // prevent the Arena from being collected.
        vtable = java.lang.foreign.Arena.global().allocate({{ vtable|ffi_struct_type_name }}.LAYOUT);
        {{ vtable|ffi_struct_type_name }}.setuniffiFree(vtable, {{ "CallbackInterfaceFree"|ffi_callback_name }}.toUpcallStub(UniffiFree.INSTANCE, java.lang.foreign.Arena.global()));
        {{ vtable|ffi_struct_type_name }}.setuniffiClone(vtable, {{ "CallbackInterfaceClone"|ffi_callback_name }}.toUpcallStub(UniffiClone.INSTANCE, java.lang.foreign.Arena.global()));
        {%- for (ffi_callback, meth) in vtable_methods.iter() %}
        {{ vtable|ffi_struct_type_name }}.set{{ meth.name()|var_name_raw }}(vtable, {{ ffi_callback.name()|ffi_callback_name }}.toUpcallStub({{ meth.name()|class_name(ci) }}Callback.INSTANCE, java.lang.foreign.Arena.global()));
        {%- endfor %}
    }

    // Registers the foreign callback with the Rust side.
    void register() {
        UniffiLib.{{ ffi_init_callback.name() }}(vtable);
    }

    {%- for (ffi_callback, meth) in vtable_methods.iter() %}
    {% let callback_class = meth.name()|class_name(ci) %}
    public static final class {{ callback_class }}Callback implements {{ ffi_callback.name()|ffi_callback_name }}.Fn {
        public static final {{ callback_class }}Callback INSTANCE = new {{ callback_class }}Callback();
        private {{ callback_class }}Callback() {}

        @Override
        public {% match ffi_callback.return_type() %}{% when Some(return_type) %}{{ return_type|ffi_type_name(config, ci) }}{% when None %}void{% endmatch %} callback(
            {%- for arg in ffi_callback.arguments() -%}
            {{ arg.type_().borrow()|ffi_type_name(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last || (loop.last && ffi_callback.has_rust_call_status_arg()) %},{% endif %}
            {%- endfor -%}
            {%- if ffi_callback.has_rust_call_status_arg() -%}
            java.lang.foreign.MemorySegment _uniffiCallStatus
            {%- endif -%}
        ) {
            {%- if ffi_callback.has_rust_call_status_arg() %}
            _uniffiCallStatus = _uniffiCallStatus.reinterpret(UniffiRustCallStatus.LAYOUT.byteSize());
            {%- endif %}
            var _uniffiObj = {{ ffi_converter_name }}.INSTANCE.handleMap.get(uniffiHandle);
            {% if !meth.is_async() && meth.throws_type().is_some() %}java.util.concurrent.Callable{% else %}java.util.function.Supplier{%endif%}<{% if meth.is_async() %}{{ meth|async_return_type(ci, config) }}{% else %}{% match meth.return_type() %}{% when Some(return_type)%}{{ return_type|type_name(ci, config)}}{% when None %}java.lang.Void{% endmatch %}{% endif %}> _uniffiMakeCall = () -> {
                {% if meth.return_type().is_some() || meth.is_async() %}return {% endif %}_uniffiObj.{{ meth.name()|fn_name() }}(
                    {%- for arg in meth.arguments() %}
                    {{ arg|lift_fn(config, ci) }}({{ arg.name()|var_name }}){% if !loop.last %},{% endif %}
                    {%- endfor %}
                );
                {% if meth.return_type().is_none() && !meth.is_async() %}return null;{% endif %}
            };
            {%- if !meth.is_async() %}
            {%- match meth.return_type() %}
            {%- when Some(return_type) %}
            {%- let ffi_return_type = return_type|ffi_type %}
            java.util.function.Consumer<{{ return_type|type_name(ci, config)}}> _uniffiWriteReturn = ({{ return_type|type_name(ci, config) }} _uniffiValue) -> {
                {%- if ffi_return_type.borrow()|ffi_type_is_embedded_struct %}
                java.lang.foreign.MemorySegment _uniffiOut = uniffiOutReturn.reinterpret({{ ffi_return_type.borrow()|ffi_struct_type_name }}.LAYOUT.byteSize());
                java.lang.foreign.MemorySegment _uniffiLowered = {{ return_type|lower_fn(config, ci) }}(_uniffiValue);
                java.lang.foreign.MemorySegment.copy(_uniffiLowered, 0, _uniffiOut, 0, {{ ffi_return_type.borrow()|ffi_struct_type_name }}.LAYOUT.byteSize());
                {%- else %}
                java.lang.foreign.MemorySegment _uniffiOut = uniffiOutReturn.reinterpret({{ ffi_return_type.borrow()|ffi_value_layout }}.byteSize());
                _uniffiOut.set({{ ffi_return_type.borrow()|ffi_value_layout_unaligned }}, 0, {{ return_type|lower_fn(config, ci) }}(_uniffiValue));
                {%- endif %}
            };
            {%- when None %}
            java.util.function.Consumer<java.lang.Void> _uniffiWriteReturn = (_uniffiNothing) -> {};
            {%- endmatch %}

            {%- match meth.throws_type() %}
            {%- when None %}
            UniffiHelpers.uniffiTraitInterfaceCall(_uniffiCallStatus, _uniffiMakeCall, _uniffiWriteReturn);
            {%- when Some(error_type) %}
            UniffiHelpers.uniffiTraitInterfaceCallWithError(
                _uniffiCallStatus,
                _uniffiMakeCall,
                _uniffiWriteReturn,
                ({{error_type|type_name(ci, config) }} _uniffiErr) -> { return {{ error_type|lower_fn(config, ci) }}(_uniffiErr); },
                {{error_type|type_name(ci, config)}}.class
            );
            {%- endmatch %}

            {%- else %}
            {#- Async callback interface method -#}
            {%- let result_struct_name = meth.foreign_future_ffi_result_struct().name()|ffi_struct_name %}
            // The completion callback always has signature: (long callbackData, java.lang.foreign.MemorySegment result) -> void
            java.lang.foreign.FunctionDescriptor _uniffiCompletionDescriptor = java.lang.foreign.FunctionDescriptor.ofVoid(
                java.lang.foreign.ValueLayout.JAVA_LONG, {{ result_struct_name }}.LAYOUT
            );
            java.util.function.Consumer<{{ meth|async_inner_return_type(ci, config) }}> _uniffiHandleSuccess = ({% match meth.return_type() %}{%- when Some(return_type) %}_uniffiReturnValue{%- when None %}_uniffiNothing{% endmatch %}) -> {
                java.lang.foreign.MemorySegment _uniffiResult = java.lang.foreign.Arena.ofAuto().allocate({{ result_struct_name }}.LAYOUT);
                {%- match meth.return_type() %}
                {%- when Some(return_type) %}
                {%- let ffi_return_type = return_type|ffi_type %}
                {%- if ffi_return_type.borrow()|ffi_type_is_embedded_struct %}
                java.lang.foreign.MemorySegment _uniffiLowered = {{ return_type|lower_fn(config, ci) }}(_uniffiReturnValue);
                {{ result_struct_name }}.setreturnValue(_uniffiResult, _uniffiLowered);
                {%- else %}
                {{ result_struct_name }}.setreturnValue(_uniffiResult, {{ return_type|lower_fn(config, ci) }}(_uniffiReturnValue));
                {%- endif %}
                {%- when None %}
                {%- endmatch %}
                // Set status to success (zeroed out already)
                try {
                    // Convert the upcall java.lang.foreign.MemorySegment to a globally-scoped one for cross-thread use
                    java.lang.foreign.MemorySegment _uniffiGlobalCallback = java.lang.foreign.MemorySegment.ofAddress(uniffiFutureCallback.address());
                    java.lang.invoke.MethodHandle _uniffiMh = java.lang.foreign.Linker.nativeLinker().downcallHandle(
                        _uniffiGlobalCallback, _uniffiCompletionDescriptor);
                    _uniffiMh.invokeExact(uniffiCallbackData, _uniffiResult);
                } catch (Throwable _uniffiThrowable) {
                    throw new AssertionError("invokeExact failed", _uniffiThrowable);
                }
            };
            java.util.function.Consumer<java.lang.foreign.MemorySegment> _uniffiHandleError = (_uniffiErrStatus) -> {
                java.lang.foreign.MemorySegment _uniffiResult = java.lang.foreign.Arena.ofAuto().allocate({{ result_struct_name }}.LAYOUT);
                {{ result_struct_name }}.setcallStatus(_uniffiResult, _uniffiErrStatus);
                try {
                    java.lang.foreign.MemorySegment _uniffiGlobalCallback = java.lang.foreign.MemorySegment.ofAddress(uniffiFutureCallback.address());
                    java.lang.invoke.MethodHandle _uniffiMh = java.lang.foreign.Linker.nativeLinker().downcallHandle(
                        _uniffiGlobalCallback, _uniffiCompletionDescriptor);
                    _uniffiMh.invokeExact(uniffiCallbackData, _uniffiResult);
                } catch (Throwable _uniffiThrowable) {
                    throw new AssertionError("invokeExact failed", _uniffiThrowable);
                }
            };

            {%- match meth.throws_type() %}
            {%- when None %}
            UniffiAsyncHelpers.uniffiTraitInterfaceCallAsync(
                _uniffiMakeCall,
                _uniffiHandleSuccess,
                _uniffiHandleError,
                uniffiOutDroppedCallback
            );
            {%- when Some(error_type) %}
            UniffiAsyncHelpers.uniffiTraitInterfaceCallAsyncWithError(
                _uniffiMakeCall,
                _uniffiHandleSuccess,
                _uniffiHandleError,
                ({{error_type|type_name(ci, config) }} _uniffiErr) -> {{ error_type|lower_fn(config, ci) }}(_uniffiErr),
                {{ error_type|type_name(ci, config)}}.class,
                uniffiOutDroppedCallback
            );
            {%- endmatch %}
            {%- endif %}
        }
    }
    {%- endfor %}

    public static class UniffiFree implements {{ "CallbackInterfaceFree"|ffi_callback_name }}.Fn {
        public static final UniffiFree INSTANCE = new UniffiFree();

        private UniffiFree() {}

        @Override
        public void callback(long handle) {
            {{ ffi_converter_name }}.INSTANCE.handleMap.remove(handle);
        }
    }

    public static class UniffiClone implements {{ "CallbackInterfaceClone"|ffi_callback_name }}.Fn {
        public static final UniffiClone INSTANCE = new UniffiClone();

        private UniffiClone() {}

        @Override
        public long callback(long handle) {
            return {{ ffi_converter_name }}.INSTANCE.handleMap.clone(handle);
        }
    }
}
