{%- let rec = ci|get_record_definition(name) %}
package {{ config.package_name() }};

import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;

{%- if rec.has_fields() %}
{%- call java::docstring(rec, 0) %}
public record {{ type_name }}(
    {%- for field in rec.fields() %}
    {%- call java::docstring(field, 4) %}
    {{ field|type_name(ci) }} {{ field.name()|var_name -}}
    {% if !loop.last %}, {% endif %}
    {%- endfor %}
) {% if contains_object_references %} implements Disposable {% endif %}{
{% if contains_object_references %}
    @Override
    public void destroy() {
        {% call java::destroy_fields(rec) %}
    }
    {% endif %}
}
{%- else -%}
{%- call java::docstring(rec, 0) %}
public class {{ type_name }} {
    @Override
    public boolean equals(Object other) {
        return other instanceof {{ type_name }};
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
{%- endif %}

package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum {{ rec|ffi_converter_name }} implements FfiConverterRustBuffer<{{ type_name }}> {
  INSTANCE;

  @Override
  public {{ type_name }} read(ByteBuffer buf) {
    {%- if rec.has_fields() %}
    return new {{ type_name }}(
    {%- for field in rec.fields() %}
      {{ field|read_fn }}(buf){% if !loop.last %},{% else %}{% endif %}
    {%- endfor %}
    );
    {%- else %}
    return new {{ type_name }}();
    {%- endif %}
  }

  @Override
  public long allocationSize({{ type_name }} value) {
      {%- if rec.has_fields() %}
      return (
        {%- for field in rec.fields() %}
            {{ field|allocation_size_fn }}(value.{{ field.name()|var_name }}()){% if !loop.last %} +{% endif %}
        {%- endfor %}
      ); 
      {%- else %}
      return 0L;
      {%- endif %}
  }

  @Override
  public void write({{ type_name }} value, ByteBuffer buf) {
    {%- for field in rec.fields() %}
      {{ field|write_fn }}(value.{{ field.name()|var_name }}(), buf);
    {%- endfor %}
  }
}
