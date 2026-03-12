{% if self.include_once_check("CallbackInterfaceRuntime.java") %}{% include "CallbackInterfaceRuntime.java" %}{% endif %}

package {{ config.package_name() }};

import java.lang.foreign.Arena;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;

{%- let trait_impl=format!("UniffiCallbackInterface{}", name) %}

// Put the implementation in an object so we don't pollute the top-level namespace
public class {{ trait_impl }} {
    private static final Arena ARENA = Arena.ofAuto();
    public static final {{ trait_impl }} INSTANCE = new {{ trait_impl }}();
    MemorySegment vtable;

    {{ trait_impl }}() {
        vtable = {{ vtable|ffi_struct_type_name }}.allocate(ARENA);
        {%- for (ffi_callback, meth) in vtable_methods.iter() %}
        {{ vtable|ffi_struct_type_name }}.set{{ meth.name()|var_name_raw }}(vtable,
            {{ ffi_callback.name()|ffi_callback_name }}.toUpcallStub({{ meth.name()|var_name }}Impl.INSTANCE, ARENA));
        {%- endfor %}
        {{ vtable|ffi_struct_type_name }}.setuniffiFree(vtable,
            {{ "CallbackInterfaceFree"|ffi_callback_name }}.toUpcallStub(UniffiFreeImpl.INSTANCE, ARENA));
    }

    // Registers the foreign callback with the Rust side.
    // This method is generated for each callback interface.
    void register() {
        UniffiLib.{{ ffi_init_callback.name() }}(vtable);
    }

    {%- for (ffi_callback, meth) in vtable_methods.iter() %}
    {% let inner_method_class = meth.name()|var_name %}
    public static class {{ inner_method_class }}Impl implements {{ ffi_callback.name()|ffi_callback_name }}.Fn {
        public static final {{ inner_method_class }}Impl INSTANCE = new {{ inner_method_class }}Impl();
        // Cached downcall handle for async completion callback.
        // The Rust scaffolding typically provides the same function pointer for all
        // completions, so caching by address avoids expensive downcallHandle() creation.
        private static volatile java.lang.invoke.MethodHandle uniffiCachedCompleteMh;
        private static volatile long uniffiCachedCompleteAddr;
        {%- if meth.is_async() %}
        // Thread-local pre-allocated result struct to avoid Arena.ofConfined() overhead.
        // Each thread gets its own segment, so no synchronization needed.
        private static final ThreadLocal<MemorySegment> RESULT_STRUCT = ThreadLocal.withInitial(() ->
            Arena.global().allocate({{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.LAYOUT)
        );
        {%- endif %}
        private {{ inner_method_class }}Impl() {}

        @Override
        public {% match ffi_callback.return_type() %}{% when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct(config, ci) }}{% when None %}void{% endmatch %} callback(
            {%- for arg in ffi_callback.arguments() -%}
            {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last || (loop.last && ffi_callback.has_rust_call_status_arg()) %},{% endif %}
            {%- endfor -%}
            {%- if ffi_callback.has_rust_call_status_arg() -%}
            MemorySegment uniffiCallStatus
            {%- endif -%}
        ) {
            var uniffiObj = {{ ffi_converter_name }}.INSTANCE.handleMap.get(uniffiHandle);
            {% if !meth.is_async() && meth.throws_type().is_some() %}Callable{% else %}Supplier{%endif%}<{% if meth.is_async() %}{{ meth|async_return_type(ci, config) }}{% else %}{% match meth.return_type() %}{% when Some(return_type)%}{{ return_type|type_name(ci, config)}}{% when None %}Void{% endmatch %}{% endif %}> makeCall = () -> {
                {% if meth.return_type().is_some() || meth.is_async() %}return {% endif %}uniffiObj.{{ meth.name()|fn_name() }}(
                    {%- for arg in meth.arguments() %}
                    {{ arg|lift_fn(config, ci) }}({{ arg.name()|var_name }}){% if !loop.last %},{% endif %}
                    {%- endfor %}
                );
                {% if meth.return_type().is_none() && !meth.is_async() %}return null;{% endif %}
            };
            {%- if !meth.is_async() %}
            {%- match meth.return_type() %}
            {%- when Some(return_type) %}
            final var uniffiOutReturnSized = uniffiOutReturn.reinterpret({{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.LAYOUT.byteSize());
            Consumer<{{ return_type|type_name(ci, config)}}> writeReturn = ({{ return_type|type_name(ci, config) }} value) -> {
                {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.setreturnValue(uniffiOutReturnSized, {{ return_type|lower_fn(config, ci) }}(value));
            };
            {%- when None %}
            Consumer<Void> writeReturn = (nothing) -> {};
            {%- endmatch %}

            {%- match meth.throws_type() %}
            {%- when None %}
            UniffiHelpers.uniffiTraitInterfaceCall(uniffiCallStatus, makeCall, writeReturn);
            {%- when Some(error_type) %}
            UniffiHelpers.uniffiTraitInterfaceCallWithError(
                uniffiCallStatus,
                makeCall,
                writeReturn,
                ({{error_type|type_name(ci, config) }} e) -> { return {{ error_type|lower_fn(config, ci) }}(e); },
                {{error_type|type_name(ci, config)}}.class
            );
            {%- endmatch %}

            {%- else %}
            // Convert confined upcall MemorySegment to globally-scoped for cross-thread access.
            // Upcall parameter segments are confined to the upcall thread, but the async
            // completion handlers run on ForkJoinPool threads.
            var uniffiFutureCallbackGlobal = MemorySegment.ofAddress(uniffiFutureCallback.address());
            Consumer<{{ meth|async_inner_return_type(ci, config) }}> uniffiHandleSuccess = ({% match meth.return_type() %}{%- when Some(return_type) %}returnValue{%- when None %}nothing{% endmatch %}) -> {
                {%- match meth.return_type() %}
                {%- when Some(return_type) %}
                // Lower the return value before accessing the result struct.
                var uniffiLoweredReturn = {{ return_type|lower_fn(config, ci) }}(returnValue);
                {%- when None %}
                {%- endmatch %}
                // Use thread-local pre-allocated result struct to avoid Arena allocation overhead.
                // The struct is consumed synchronously by invokeExact before we return.
                var uniffiResult = RESULT_STRUCT.get();
                {%- match meth.return_type() %}
                {%- when Some(return_type) %}
                {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.setreturnValue(uniffiResult, uniffiLoweredReturn);
                {%- when None %}
                {%- endmatch %}
                // Zero the call status inline (success = code 0, no error buffer).
                var callStatusSlice = {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.getcallStatus(uniffiResult);
                callStatusSlice.fill((byte) 0);
                try {
                    long addr = uniffiFutureCallbackGlobal.address();
                    java.lang.invoke.MethodHandle completeMh = uniffiCachedCompleteMh;
                    if (uniffiCachedCompleteAddr != addr || completeMh == null) {
                        completeMh = Linker.nativeLinker().downcallHandle(
                            uniffiFutureCallbackGlobal,
                            {{ ffi_callback|async_completion_callback_name }}.DESCRIPTOR
                        );
                        uniffiCachedCompleteAddr = addr;
                        uniffiCachedCompleteMh = completeMh;
                    }
                    completeMh.invokeExact(uniffiCallbackData, uniffiResult);
                } catch (Throwable _ex) {
                    throw new AssertionError("Unexpected exception from FFI callback invocation", _ex);
                }
            };
            Consumer<MemorySegment> uniffiHandleError = (callStatus) -> {
                // Use thread-local pre-allocated result struct to avoid Arena allocation overhead.
                var uniffiResult = RESULT_STRUCT.get();
                {%- match meth.return_type() %}
                {%- when Some(return_type) %}
                {%- when None %}
                {%- endmatch %}
                {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.setcallStatus(uniffiResult, callStatus);
                try {
                    long addr = uniffiFutureCallbackGlobal.address();
                    java.lang.invoke.MethodHandle completeMh = uniffiCachedCompleteMh;
                    if (uniffiCachedCompleteAddr != addr || completeMh == null) {
                        completeMh = Linker.nativeLinker().downcallHandle(
                            uniffiFutureCallbackGlobal,
                            {{ ffi_callback|async_completion_callback_name }}.DESCRIPTOR
                        );
                        uniffiCachedCompleteAddr = addr;
                        uniffiCachedCompleteMh = completeMh;
                    }
                    completeMh.invokeExact(uniffiCallbackData, uniffiResult);
                } catch (Throwable _ex) {
                    throw new AssertionError("Unexpected exception from FFI callback invocation", _ex);
                }
            };

            var foreignFuture =
                {%- match meth.throws_type() %}
                {%- when None %}
                UniffiAsyncHelpers.uniffiTraitInterfaceCallAsync(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError
                );
                {%- when Some(error_type) %}
                UniffiAsyncHelpers.uniffiTraitInterfaceCallAsyncWithError(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError,
                    ({{error_type|type_name(ci, config) }} e) -> {{ error_type|lower_fn(config, ci) }}(e),
                    {{ error_type|type_name(ci, config)}}.class
                );
                {%- endmatch %}
            var uniffiOutReturnSized = uniffiOutReturn.reinterpret({{ "ForeignFuture"|ffi_struct_name }}.LAYOUT.byteSize());
            {{ "ForeignFuture"|ffi_struct_name }}.sethandle(uniffiOutReturnSized, foreignFuture.handle);
            {{ "ForeignFuture"|ffi_struct_name }}.setfree(uniffiOutReturnSized, foreignFuture.free);
            {%- endif %}
        }
    }
    {%- endfor %}

    public static class UniffiFreeImpl implements {{ "CallbackInterfaceFree"|ffi_callback_name }}.Fn {
        public static final UniffiFreeImpl INSTANCE = new UniffiFreeImpl();

        private UniffiFreeImpl() {}

        @Override
        public void callback(long handle) {
            {{ ffi_converter_name }}.INSTANCE.handleMap.remove(handle);
        }
    }
}
