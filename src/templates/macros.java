{#
// Template to call into rust. Used in several places.
// Variable names in `arg_list` should match up with arg lists
// passed to rust via `arg_list_lowered`
#}

{%- macro to_ffi_call(func) -%}
    {%- if func.self_type().is_some() %}
    callWithHandle(uniffiHandle -> {
        try {
    {% if func.return_type().is_some() %}
            return {%- call to_raw_ffi_call(func) %};
    {% else %}
            {%- call to_raw_ffi_call(func) %};
    {% endif %}
        } catch (java.lang.Exception e) {
            throw new RuntimeException(e);
        }
    })
    {% else %}
        {%- call to_raw_ffi_call(func) %}
    {% endif %}
{%- endmacro %}

{%- macro to_raw_ffi_call(func) -%}
    {%- match func.throws_type() %}
    {%- when Some(e) %}
    {%- if e|is_external(ci) %}
    UniffiHelpers.uniffiRustCallWithError(new {{ e|class_name_from_type(ci) }}ExternalErrorHandler(),
    {%- else %}
    UniffiHelpers.uniffiRustCallWithError(new {{ e|type_name(ci, config) }}ErrorHandler(),
    {%- endif %}
    {%- else %}
    UniffiHelpers.uniffiRustCall(
    {%- endmatch %} _status -> {
        {% if func.return_type().is_some() %}return {% endif %}UniffiLib.{{ func.ffi_func().name() }}(
            {% if func.self_type().is_some() %}uniffiHandle, {% endif -%}
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
    {{ func_decl }} CompletableFuture<{% match callable.return_type() -%}{%- when Some with (return_type) -%}{{ return_type|type_name(ci, config) }}{%- when None %}Void{%- endmatch %}> {{ callable.name()|fn_name }}(
        {%- call arg_list(callable, !callable.self_type().is_some()) -%}
    ){
        return {% call call_async(callable) %};
    }
    {%- else -%}
    {{ func_decl }} {% match callable.return_type() -%}{%- when Some with (return_type) -%}{{ return_type|type_name(ci, config) }}{%- when None %}void{%- endmatch %} {{ callable.name()|fn_name }}(
        {%- call arg_list(callable, !callable.self_type().is_some()) -%}
    ) {% match callable.throws_type() -%}
        {%-     when Some(throwable) -%}
        throws {{ throwable|type_name(ci, config) }}
        {%-     else -%}
        {%- endmatch %} {
            try {
                {% match callable.return_type() -%}{%- when Some with (return_type) -%}return {{ return_type|lift_fn(config, ci) }}({% call to_ffi_call(callable) %}){%- when None %}{% call to_ffi_call(callable) %}{%- endmatch %};
            } catch (RuntimeException _e) {
                {% match callable.throws_type() %}
                {% when Some(throwable) %}
                if ({{ throwable|type_name(ci, config) }}.class.isInstance(_e.getCause())) {
                    throw ({{ throwable|type_name(ci, config) }})_e.getCause();
                }
                {% else %}
                {% endmatch %}
                if (InternalException.class.isInstance(_e.getCause())) {
                    throw (InternalException)_e.getCause();
                }
                throw _e;
            }
    }
    {% endif %}
{% endmacro %}

{%- macro call_async(callable) -%}
    UniffiAsyncHelpers.uniffiRustCallAsync(
{%- if callable.self_type().is_some() %}
        callWithHandle(uniffiHandle -> {
            return UniffiLib.{{ callable.ffi_func().name() }}(
                uniffiHandle{% if callable.arguments().len() != 0 %},{% endif %}
                {% call arg_list_lowered(callable) %}
            );
        }),
{%- else %}
        UniffiLib.{{ callable.ffi_func().name() }}({% call arg_list_lowered(callable) %}),
{%- endif %}
        {{ callable|async_poll(ci) }},
        {{ callable|async_complete(ci, config) }},
        {{ callable|async_free(ci) }},
        // lift function
        {%- match callable.return_type() %}
        {%- when Some(return_type) %}
        (it) -> {{ return_type|lift_fn(config, ci) }}(it),
        {%- when None %}
        () -> {},
        {%- endmatch %}
        // Error FFI converter
        {%- match callable.throws_type() %}
        {%- when Some(e) %}
        {%- if e|is_external(ci) %}
        new {{ e|class_name_from_type(ci) }}ExternalErrorHandler()
        {%- else %}
        new {{ e|type_name(ci, config) }}ErrorHandler()
        {%- endif %}
        {%- when None %}
        new UniffiNullRustCallStatusErrorHandler()
        {%- endmatch %}
    )
{%- endmacro %}

{%- macro arg_list_lowered(func) %}
    {%- for arg in func.arguments() %}
        {{- arg|lower_fn(config, ci) }}({{ arg.name()|var_name }})
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
        {{ arg|type_name(ci, config) }} {{ arg.name()|var_name }}
{%-     if !loop.last %}, {% endif -%}
{%- endfor %}
{%- endmacro %}

{#-
// Arglist as used in the UniffiLib function declarations.
// Note unfiltered name but ffi_type_name filters.
-#}
{%- macro arg_list_ffi_decl(func) %}
    {%- for arg in func.arguments() %}
        {{- arg.type_().borrow()|ffi_type_name_by_value(config, ci) }} {{arg.name()|var_name -}}{%- if !loop.last %}, {% endif -%}
    {%- endfor %}
    {%- if func.has_rust_call_status_arg() %}{% if func.arguments().len() != 0 %}, {% endif %}UniffiRustCallStatus uniffi_out_errmk{% endif %}
{%- endmacro -%}

{#-
// Arglist for JNA direct mapping native method declarations.
// Uses primitive types instead of boxed types.
-#}
{%- macro arg_list_ffi_decl_primitive(func) %}
    {%- for arg in func.arguments() %}
        {{- arg.type_().borrow()|ffi_type_name_primitive(config, ci) }} {{arg.name()|var_name -}}{%- if !loop.last %}, {% endif -%}
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

{% macro field_name_unquoted(field, field_num) %}
{%- if field.name().is_empty() -%}
v{{- field_num -}}
{%- else -%}
{{ field.name()|var_name|unquote }}
{%- endif -%}
{%- endmacro %}

// Macro for destroying fields
{%- macro destroy_fields(member) %}
    AutoCloseableHelper.close(
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

{# Macro for uniffi_trait implementations - Display, Eq, Hash, Ord #}
{%- macro uniffi_trait_impls(uniffi_trait_methods, type_name) %}
{# Prefer Display, fall back to Debug #}
{%- if let Some(fmt) = uniffi_trait_methods.display_fmt.or(uniffi_trait_methods.debug_fmt.clone()) %}
    @Override
    public String toString() {
        return {{ fmt.return_type().unwrap()|lift_fn(config, ci) }}({% call to_ffi_call(fmt) %});
    }
{%- endif %}
{%- if let Some(eq) = uniffi_trait_methods.eq_eq %}
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof {{ type_name }})) return false;
        {{ type_name }} other = ({{ type_name }}) obj;
        return {{ eq.return_type().unwrap()|lift_fn(config, ci) }}({% call to_ffi_call(eq) %});
    }
{%- endif %}
{%- if let Some(hash) = uniffi_trait_methods.hash_hash %}
    @Override
    public int hashCode() {
        return {{ hash.return_type().unwrap()|lift_fn(config, ci) }}({%- call to_ffi_call(hash) %}).intValue();
    }
{%- endif %}
{%- if let Some(cmp) = uniffi_trait_methods.ord_cmp %}
    @Override
    public int compareTo({{ type_name }} other) {
        if (other == null) throw new NullPointerException();
        return {{ cmp.return_type().unwrap()|lift_fn(config, ci) }}({%- call to_ffi_call(cmp) %}).intValue();
    }
{%- endif %}
{%- endmacro %}
