{% if self.include_once_check("CallbackInterfaceRuntime.java") %}{% include "CallbackInterfaceRuntime.java" %}{% endif %}

package {{ config.package_name() }};

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;
import com.sun.jna.*;
import com.sun.jna.ptr.*;

{%- let trait_impl=format!("UniffiCallbackInterface{}", name) %}

// Put the implementation in an object so we don't pollute the top-level namespace
public class {{ trait_impl }} {
    public static final {{ trait_impl }} INSTANCE = new {{ trait_impl }}();
    {{ vtable|ffi_type_name_by_value(config, ci) }} vtable;
    
    {{ trait_impl }}() {
        vtable = new {{ vtable|ffi_type_name_by_value(config, ci) }}(
            {%- for (ffi_callback, meth) in vtable_methods.iter() %}
            {{ meth.name()|var_name }}.INSTANCE,
            {%- endfor %}
            UniffiFree.INSTANCE
        );
    }
    
    // Registers the foreign callback with the Rust side.
    // This method is generated for each callback interface.
    void register(UniffiLib lib) {
        lib.{{ ffi_init_callback.name() }}(vtable);
    }        

    {%- for (ffi_callback, meth) in vtable_methods.iter() %}
    {% let inner_method_class = meth.name()|var_name %}
    public static class {{ inner_method_class }} implements {{ ffi_callback.name()|ffi_callback_name }} {
        public static final {{ inner_method_class }} INSTANCE = new {{ inner_method_class }}();
        private {{ inner_method_class }}() {}

        @Override
        public {% match ffi_callback.return_type() %}{% when Some(return_type) %}{{ return_type|ffi_type_name_for_ffi_struct(config, ci) }}{% when None %}void{% endmatch %} callback(
            {%- for arg in ffi_callback.arguments() -%}
            {{ arg.type_().borrow()|ffi_type_name_for_ffi_struct(config, ci) }} {{ arg.name().borrow()|var_name }}{% if !loop.last || (loop.last && ffi_callback.has_rust_call_status_arg()) %},{% endif %}
            {%- endfor -%}
            {%- if ffi_callback.has_rust_call_status_arg() -%}
            UniffiRustCallStatus uniffiCallStatus
            {%- endif -%}
        ) {
            var uniffiObj = {{ ffi_converter_name }}.INSTANCE.handleMap.get(uniffiHandle);
            {% if !meth.is_async() && meth.throws_type().is_some() %}Callable{% else %}Supplier{%endif%}<{% if meth.is_async() %}{{ meth|async_return_type(ci, config) }}{% else %}{% match meth.return_type() %}{% when Some(return_type)%}{{ return_type|type_name(ci, config)}}{% when None %}Void{% endmatch %}{% endif %}> makeCall = () -> {
                {% if meth.return_type().is_some() || meth.is_async() %}return {% endif %}uniffiObj.{{ meth.name()|fn_name() }}(
                    {%- for arg in meth.arguments() %}
                    {{ arg|lift_fn(config) }}({{ arg.name()|var_name }}){% if !loop.last %},{% endif %}
                    {%- endfor %}
                );
                {% if meth.return_type().is_none() && !meth.is_async() %}return null;{% endif %}
            };
            {%- if !meth.is_async() %}
            {%- match meth.return_type() %}
            {%- when Some(return_type) %}
            Consumer<{{ return_type|type_name(ci, config)}}> writeReturn = ({{ return_type|type_name(ci, config) }} value) -> { uniffiOutReturn.setValue({{ return_type|lower_fn(config) }}(value)); };
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
                ({{error_type|type_name(ci, config) }} e) -> { return {{ error_type|lower_fn(config) }}(e); },
                {{error_type|type_name(ci, config)}}.class
            );
            {%- endmatch %}

            {%- else %}
            Consumer<{{ meth|async_inner_return_type(ci, config) }}> uniffiHandleSuccess = ({% match meth.return_type() %}{%- when Some(return_type) %}returnValue{%- when None %}nothing{% endmatch %}) -> {
                var uniffiResult = new {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.UniffiByValue(
                    {%- match meth.return_type() %}
                    {%- when Some(return_type) %}
                    {{ return_type|lower_fn(config) }}(returnValue),
                    {%- when None %}
                    {%- endmatch %}
                    new UniffiRustCallStatus.ByValue()
                );
                uniffiResult.write();
                uniffiFutureCallback.callback(uniffiCallbackData, uniffiResult);
            };
            Consumer<UniffiRustCallStatus.ByValue> uniffiHandleError = (callStatus) -> {
                uniffiFutureCallback.callback(
                    uniffiCallbackData,
                    new {{ meth.foreign_future_ffi_result_struct().name()|ffi_struct_name }}.UniffiByValue(
                        {%- match meth.return_type() %}
                        {%- when Some(return_type) %}
                        {{ return_type.into()|ffi_default_value }},
                        {%- when None %}
                        {%- endmatch %}
                        callStatus
                    )
                );
            };

            uniffiOutReturn.uniffiSetValue(
                {%- match meth.throws_type() %}
                {%- when None %}
                UniffiAsyncHelpers.uniffiTraitInterfaceCallAsync(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError
                )
                {%- when Some(error_type) %}
                UniffiAsyncHelpers.uniffiTraitInterfaceCallAsyncWithError(
                    makeCall,
                    uniffiHandleSuccess,
                    uniffiHandleError,
                    ({{error_type|type_name(ci, config) }} e) -> {{ error_type|lower_fn(config) }}(e),
                    {{ error_type|type_name(ci, config)}}.class
                )
                {%- endmatch %}
            );
            {%- endif %}
        }
    }
    {%- endfor %}

    public static class UniffiFree implements {{ "CallbackInterfaceFree"|ffi_callback_name }} {
        public static final UniffiFree INSTANCE = new UniffiFree();

        private UniffiFree() {}

        @Override
        public void callback(long handle) {
            {{ ffi_converter_name }}.INSTANCE.handleMap.remove(handle);
        }
    }
}
