{#
// Template to call into rust. Used in several places.
// Variable names in `arg_list` should match up with arg lists
// passed to rust via `arg_list_lowered`
#}

{%- macro to_ffi_call(func) -%}
    {%- if func.takes_self() %}
    callWithPointer(it -> {
        try {
    {% if func.return_type().is_some() %}
            return {%- call to_raw_ffi_call(func) %};
    {% else %}
            {%- call to_raw_ffi_call(func) %};
    {% endif %}
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    })
    {% else %}
        {%- call to_raw_ffi_call(func) %}
    {% endif %}
{%- endmacro %}

{%- macro to_raw_ffi_call(func) -%}
    {%- match func.throws_type() %}
    {%- when Some with (e) %}
    UniffiHelpers.uniffiRustCallWithError(new {{ e|type_name(ci) }}ErrorHandler(), 
    {%- else %}
    UniffiHelpers.uniffiRustCall(
    {%- endmatch %} _status -> {
        {% if func.return_type().is_some() %}return {% endif %}UniffiLib.INSTANCE.{{ func.ffi_func().name() }}(
            {% if func.takes_self() %}it, {% endif -%}
            {% if func.arguments().len() != 0 %}{% call arg_list_lowered(func) -%}, {% endif -%}
            _status);
    })
{%- endmacro -%}

{%- macro func_decl(func_decl, annotation, callable, indent) %}
    {%- call docstring(callable, indent) %}
    {%- if annotation != "" %}
    @{{ annotation }}
    {% endif %}
    {%- if callable.is_async() %}
    {{ func_decl }} CompletableFuture<{% match callable.return_type() -%}{%- when Some with (return_type) -%}{{ return_type|type_name(ci) }}{%- when None %}Void{%- endmatch %}> {{ callable.name()|fn_name }}(
        {%- call arg_list(callable, !callable.takes_self()) -%}
    ){
        return {% call call_async(callable) %};
    }
    {%- else -%}
    {{ func_decl }} {% match callable.return_type() -%}{%- when Some with (return_type) -%}{{ return_type|type_name(ci) }}{%- when None %}void{%- endmatch %} {{ callable.name()|fn_name }}(
        {%- call arg_list(callable, !callable.takes_self()) -%}
    ) {% match callable.throws_type() -%}
        {%-     when Some(throwable) -%}
        throws {{ throwable|type_name(ci) }}
        {%-     else -%}
        {%- endmatch %} {
            // TODO(murph): to_ffi_call(callable) may throw `callable.throws_type` in a RuntimeException because Java closures can't throw. Now that we're in something declaring that throw we should re-catch it and throw it again
            {% match callable.return_type() -%}{%- when Some with (return_type) -%}return {{ return_type|lift_fn }}({% call to_ffi_call(callable) %}){%- when None %}{% call to_ffi_call(callable) %}{%- endmatch %};
    }
    {% endif %}
{% endmacro %}

{%- macro call_async(callable) -%}
    UniffiAsyncHelpers.uniffiRustCallAsync(
{%- if callable.takes_self() %}
        callWithPointer(thisPtr -> {
            return UniffiLib.INSTANCE.{{ callable.ffi_func().name() }}(
                thisPtr{% if callable.arguments().len() != 0 %},{% endif %}
                {% call arg_list_lowered(callable) %}
            );
        }),
{%- else %}
        UniffiLib.INSTANCE.{{ callable.ffi_func().name() }}({% call arg_list_lowered(callable) %}),
{%- endif %}
        {{ callable|async_poll(ci) }},
        {{ callable|async_complete(ci) }},
        {{ callable|async_free(ci) }},
        // lift function
        {%- match callable.return_type() %}
        {%- when Some(return_type) %}
        (it) -> {{ return_type|lift_fn }}(it),
        {%- when None %}
        () -> {},
        {%- endmatch %}
        // Error FFI converter
        {%- match callable.throws_type() %}
        {%- when Some(e) %}
        new {{ e|type_name(ci) }}ErrorHandler()
        {%- when None %}
        new UniffiNullRustCallStatusErrorHandler()
        {%- endmatch %}
    )
{%- endmacro %}

{%- macro arg_list_lowered(func) %}
    {%- for arg in func.arguments() %}
        {{- arg|lower_fn }}({{ arg.name()|var_name }})
    {%- if !loop.last %}, {% endif -%}
    {%- endfor %}
{%- endmacro -%}

{#-
// Arglist as used in Java declarations of methods, functions and constructors.
// even if `is_decl` there won't be default values, Java doesn't support them in any reasonable way.
// Note the var_name and type_name filters.
-#}

{% macro arg_list(func, is_decl) %}
{%- for arg in func.arguments() -%}
        {{ arg|type_name(ci) }} {{ arg.name()|var_name }}
{%-     if !loop.last %}, {% endif -%}
{%- endfor %}
{%- endmacro %}

{#-
// Arglist as used in the UniffiLib function declarations.
// Note unfiltered name but ffi_type_name filters.
-#}
{%- macro arg_list_ffi_decl(func) %}
    {%- for arg in func.arguments() %}
        {{- arg.type_().borrow()|ffi_type_name_by_value }} {{arg.name()|var_name -}}{%- if !loop.last %}, {% endif -%}
    {%- endfor %}
    {%- if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}UniffiRustCallStatus uniffi_out_errmk{% endif %}
{%- endmacro -%}

{% macro field_name(field, field_num) %}
{%- if field.name().is_empty() -%}
v{{- field_num -}}
{%- else -%}
{{ field.name()|var_name }}
{%- endif -%}
{%- endmacro %}

// Macro for destroying fields
{%- macro destroy_fields(member) %}
    Disposable.destroy(
    {%- for field in member.fields() %}
        this.{{ field.name()|var_name }}{%- if !loop.last %}, {% endif -%}
    {% endfor -%});
{%- endmacro -%}

{%- macro docstring_value(maybe_docstring, indent_spaces) %}
{%- match maybe_docstring %}
{%- when Some(docstring) %}
{{ docstring|docstring(indent_spaces) }}
{%- else %}
{%- endmatch %}
{%- endmacro %}

{%- macro docstring(defn, indent_spaces) %}
{%- call docstring_value(defn.docstring(), indent_spaces) %}
{%- endmacro %}
