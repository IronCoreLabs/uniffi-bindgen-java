package {{ config.package_name() }};

import java.util.List;
import java.util.Map;

{%- call java::docstring_value(interface_docstring, 0) %}
public interface {{ interface_name }} {
    {% for meth in methods.iter() -%}
    {%- call java::docstring(meth, 4) %}
    {# TODO(murph): async
    {% if meth.is_async() -%}suspend {% endif -%}
    #}
    public {% match meth.return_type() -%}{%- when Some with (return_type) %}{{ return_type|type_name(ci) }}{%- else -%}void{%- endmatch %} {{ meth.name()|fn_name }}({% call java::arg_list(meth, true) %});
    {% endfor %}
}
