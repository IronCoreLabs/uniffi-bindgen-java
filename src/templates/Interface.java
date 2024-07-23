package {{ config.package_name() }};

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.sun.jna.*;
import com.sun.jna.ptr.*;

{%- call java::docstring_value(interface_docstring, 0) %}
public interface {{ interface_name }} {
    {% for meth in methods.iter() -%}
    {%- call java::docstring(meth, 4) %}
    public {% if meth.is_async() %}CompletableFuture<{% endif %}{% match meth.return_type() -%}{%- when Some with (return_type) %}{{ return_type|type_name(ci, config) }}{%- else -%}{% if meth.is_async() %}Void{% else %}void{% endif %}{%- endmatch %}{% if meth.is_async() %}>{% endif %} {{ meth.name()|fn_name }}({% call java::arg_list(meth, true) %}){% match meth.throws_type() %}{% when Some(throwable) %} {% if !meth.is_async() %}throws {{ throwable|type_name(ci, config) }}{% endif %}{% else %}{% endmatch %};
    {% endfor %}
}
