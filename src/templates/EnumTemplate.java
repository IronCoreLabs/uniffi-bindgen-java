package {{ config.package_name() }};

import java.util.List;
import java.util.Map;

{%- if e.is_flat() %}
{% call java::docstring(e, 0) %}
{% match e.variant_discr_type() %}
{% when None %}
public enum {{ type_name }} {
  {%- for variant in e.variants() -%}
  {%- call java::docstring(variant, 4) %}
  {{ variant|variant_name}}{% if loop.last %};{% else %},{% endif %}
  {%- endfor %}
}
{% when Some with (variant_discr_type) %}
public enum {{ type_name }} {
  {% for variant in e.variants() -%}
  {%- call java::docstring(variant, 4) %}
  {{ variant|variant_name}}({{ e|variant_discr_literal(loop.index0)}}){% if loop.last %};{% else %},{% endif %}}
  {%- endfor %}

  private final {{ variant_discr_type|type_name(ci, config) }} value;
  {{type_name}}({{ variant_discr_type|type_name(ci, config) }} value) {
    this.value = value;
  }
}
{% endmatch %}

package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum {{ e|ffi_converter_name}} implements FfiConverterRustBuffer<{{ type_name }}> {
    INSTANCE;

    @Override
    public {{ type_name }} read(ByteBuffer buf) {
        try {
            return {{ type_name }}.values()[buf.getInt() - 1];
        } catch (IndexOutOfBoundsException e) {
            throw new RuntimeException("invalid enum value, something is very wrong!!", e);
        }
    }

    @Override
    public long allocationSize({{ type_name }} value) {
        return 4L;
    }

    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
        buf.putInt(value.ordinal() + 1);
    }
}

{% else %}

{%- call java::docstring(e, 0) %}
public sealed interface {{ type_name }}{% if contains_object_references %} extends AutoCloseable {% endif %} {
  {% for variant in e.variants() -%}
  {%- call java::docstring(variant, 4) %}
  {% if !variant.has_fields() -%}
  record {{ variant|type_name(ci, config)}}() implements {{ type_name }} {
    {% if contains_object_references %}
    @Override
    public void close() {
      // Nothing to destroy
    }
    {% endif %}
  }
  {% else -%}
  record {{ variant|type_name(ci, config)}}(
    {%- for field in variant.fields()  -%}
    {%- call java::docstring(field, 8) %}
    {{ field|type_name(ci, config)}} {% call java::field_name(field, loop.index) %}{% if loop.last %}{% else %}, {% endif %}
    {%- endfor -%}
  ) implements {{ type_name }} {
    {% if contains_object_references %}
    @Override
    public void close() {
      {% call java::destroy_fields(variant) %}
    }
    {% endif %}
  }
  {%- endif %}
  {% endfor %}
}

package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum {{ e|ffi_converter_name}} implements FfiConverterRustBuffer<{{ type_name }}> {
    INSTANCE;

    @Override
    public {{ type_name }} read(ByteBuffer buf) {
      return switch (buf.getInt()) {
        {%- for variant in e.variants() %}
        case {{ loop.index }} -> new {{ type_name }}.{{variant|type_name(ci, config)}}(
          {%- if variant.has_fields() -%}
          {% for field in variant.fields() -%}
          {{ field|read_fn(config) }}(buf){% if loop.last %}{% else %},{% endif %}
          {% endfor -%}
          {%- endif %});
        {%- endfor %}
        default ->
          throw new RuntimeException("invalid enum value, something is very wrong!");
      };
    }

    @Override
    public long allocationSize({{ type_name }} value) {
        return switch (value) {
          {%- for variant in e.variants() %}
          case {{ type_name }}.{{ variant|type_name(ci, config) }}({%- for field in variant.fields() %}var {% call java::field_name(field, loop.index) -%}{% if !loop.last%}, {% endif %}{% endfor %}) ->
            (4L
            {%- for field in variant.fields() %}
            + {{ field|allocation_size_fn(config) }}({%- call java::field_name(field, loop.index) -%})
            {%- endfor %});
          {%- endfor %}
        };
    }

    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
      switch (value) {
        {%- for variant in e.variants() %}
        case {{ type_name }}.{{ variant|type_name(ci, config) }}({%- for field in variant.fields() %}var {% call java::field_name(field, loop.index) -%}{% if !loop.last%}, {% endif %}{% endfor %}) -> {
          buf.putInt({{ loop.index }});
          {%- for field in variant.fields() %}
          {{ field|write_fn(config) }}({%- call java::field_name(field, loop.index) -%}, buf);
          {%- endfor %}
        }
        {%- endfor %}
      };
    }
}

{% endif %}
