package {{ config.package_name() }};

{%- call java::docstring_value(interface_docstring, 0) %}
public interface {{ interface_name }} {
    {% for meth in methods.iter() -%}
    {%- call java::docstring(meth, 4) %}
    {#- Async methods use CompletableFuture<T> which requires boxed types -#}
    public {% if meth.is_async() %}java.util.concurrent.CompletableFuture<{% endif %}{% match meth.return_type() -%}{%- when Some with (return_type) %}{% if meth.is_async() %}{{ return_type|boxed_type_name(ci, config) }}{% else %}{{ return_type|type_name_for_field(ci, config) }}{% endif %}{%- else -%}{% if meth.is_async() %}java.lang.Void{% else %}void{% endif %}{%- endmatch %}{% if meth.is_async() %}>{% endif %} {{ meth.name()|fn_name }}({% call java::arg_list(meth, true) %}){% match meth.throws_type() %}{% when Some(throwable) %} {% if !meth.is_async() %}throws {{ throwable|type_name(ci, config) }}{% endif %}{% else %}{% endmatch %};
    {% endfor %}
}
