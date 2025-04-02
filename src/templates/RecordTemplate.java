{%- let rec = ci.get_record_definition(name).unwrap() %}
package {{ config.package_name() }};

import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;
import java.util.Objects;

{%- call java::docstring(rec, 0) %}
{%- if rec.has_fields() %}
{%- if config.generate_immutable_records() %}
public record {{ type_name }}(
    {%- for field in rec.fields() %}
    {%- call java::docstring(field, 4) %}
    {{ field|type_name(ci, config) }} {{ field.name()|var_name -}}
    {% if !loop.last %}, {% endif %}
    {%- endfor %}
) {% if contains_object_references %}implements AutoCloseable {% endif %}{
    {% if contains_object_references %}
    @Override
    public void close() {
        {% call java::destroy_fields(rec) %}
    }
    {% endif %}
}
{% else %}
public class {{ type_name }} {% if contains_object_references %}implements AutoCloseable {% endif %}{
    {%- for field in rec.fields() %}
    {%- call java::docstring(field, 4) %}
    private {{ field|type_name(ci, config) }} {{ field.name()|var_name -}};
    {%- endfor %}

    public {{ type_name }}(
        {%- for field in rec.fields() %}
        {{ field|type_name(ci, config) }} {{ field.name()|var_name -}}
        {% if !loop.last %}, {% endif %}
        {%- endfor %}
    ) {
        {%- for field in rec.fields() %}
        {% let field_var_name = field.name()|var_name  %}
        this.{{ field_var_name }} = {{ field_var_name -}};
        {%- endfor %}
    }

    {%- for field in rec.fields() %}
    {% let field_var_name = field.name()|var_name %}
    public {{ field|type_name(ci, config) }} {{ field_var_name }}() {
        return this.{{ field_var_name }};
    }
    {%- endfor %}

    {%- for field in rec.fields() %}
    {%- let field_var_name = field.name()|var_name %}
    public void {{ field.name()|setter}}({{ field|type_name(ci, config) }} {{ field_var_name }}) {
        this.{{ field_var_name }} = {{ field_var_name }};
    }
    {%- endfor %}

    {% if contains_object_references %}
    @Override
    public void close() {
        {% call java::destroy_fields(rec) %}
    }
    {% endif %}
    
    @Override
    public boolean equals(Object other) {
        if (other instanceof {{ type_name }}) {
            {{ type_name }} t = ({{ type_name }}) other;
            return ({% for field in rec.fields() %}{% let field_var_name = field.name()|var_name %}
              {#- currently all primitives are already referenced by their boxed values in generated code, so `.equals` works for everything #}
              Objects.equals({{ field_var_name }}, t.{{ field_var_name }}){% if !loop.last%} && {% endif %}
              {% endfor %}
            );
        };
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash({% for field in rec.fields() %}{{ field.name()|var_name }}{% if !loop.last%}, {% endif %}{% endfor %});
    }
}
{% endif %}
{%- else %}
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
      {{ field|read_fn(config) }}(buf){% if !loop.last %},{% else %}{% endif %}
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
            {{ field|allocation_size_fn(config) }}(value.{{ field.name()|var_name }}()){% if !loop.last %} +{% endif %}
        {%- endfor %}
      ); 
      {%- else %}
      return 0L;
      {%- endif %}
  }

  @Override
  public void write({{ type_name }} value, ByteBuffer buf) {
    {%- for field in rec.fields() %}
      {{ field|write_fn(config) }}(value.{{ field.name()|var_name }}(), buf);
    {%- endfor %}
  }
}
