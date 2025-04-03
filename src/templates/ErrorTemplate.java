package {{ config.package_name() }};

{%- let type_name = type_|type_name(ci, config) %}
{%- let ffi_converter_instance = type_|ffi_converter_instance(config) %}
{%- let canonical_type_name = type_|canonical_name %}

{% if e.is_flat() %}
{%- call java::docstring(e, 0) %}
public class {{ type_name }} extends Exception {
    private {{ type_name }}(String message) {
      super(message);
    }

    {% for variant in e.variants() -%}
    {%- call java::docstring(variant, 4) %}
    public static class {{ variant|error_variant_name }} extends {{ type_name }}{% if contains_object_references %}, AutoCloseable{% endif %} {
      public {{ variant|error_variant_name }}(String message) {
        super(message);
      }
    }
    {% endfor %}
}


{%- else %}
{%- call java::docstring(e, 0) %}
public class {{ type_name }} extends Exception {
    private {{ type_name }}(String message) {
      super(message); 
    }

    {% for variant in e.variants() -%}
    {%- call java::docstring(variant, 4) %}
    {%- let variant_name = variant|error_variant_name %}
    public static class {{ variant_name }} extends {{ type_name }}{% if contains_object_references %}, AutoCloseable{% endif %} {
      {% for field in variant.fields() -%}
      {%- call java::docstring(field, 8) %}
      {{ field|type_name(ci, config) }} {{ field.name()|var_name }};
      {% endfor -%}

      public {{ variant_name }}(
        {%- for field in variant.fields() -%}
        {{ field|type_name(ci, config)}} {{ field.name()|var_name }}{% if loop.last %}{% else %}, {% endif %}
        {%- endfor -%}
      ) {
        super(new StringBuilder()
        {%- for field in variant.fields() %}
        .append("{% call java::field_name_unquoted(field, loop.index) %}=")
        .append({% call java::field_name(field, loop.index) %})
        {% if !loop.last %}
        .append(", ")
        {% endif %}
        {% endfor %}
        .toString());
        {% for field in variant.fields() -%}
        this.{{ field.name()|var_name }} = {{ field.name()|var_name }};
        {% endfor -%}   
      }

      {% for field in variant.fields() -%}
      public {{ field|type_name(ci, config) }} {{ field.name()|var_name}}() {
        return this.{{ field.name()|var_name }};
      }
      {% endfor %}
      
      {% if contains_object_references %}
      @Override
      void close() {
        {%- if variant.has_fields() %}
        {% call java::destroy_fields(variant) %}
        {% else -%}
        // Nothing to destroy
        {%- endif %}
      }
      {% endif %}
    }
    {% endfor %} 
}
{%- endif %}

package {{ config.package_name() }};

public class {{ type_name }}ErrorHandler implements UniffiRustCallStatusErrorHandler<{{ type_name }}> {
  @Override
  public {{ type_name }} lift(RustBuffer.ByValue errorBuf){
     return {{ ffi_converter_instance }}.lift(errorBuf);
  }
}

package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum {{ e|ffi_converter_name }} implements FfiConverterRustBuffer<{{ type_name }}> {
    INSTANCE;

    @Override
    public {{ type_name }} read(ByteBuffer buf) {
        {%- if e.is_flat() %}
        return switch(buf.getInt()) {
            {%- for variant in e.variants() %}
            case {{ loop.index }} -> new {{ type_name }}.{{ variant|error_variant_name }}({{ Type::String.borrow()|read_fn(config) }}(buf));
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
        {%- else %}

        return switch(buf.getInt()) {
            {%- for variant in e.variants() %}
            case {{ loop.index }} -> new {{ type_name }}.{{ variant|error_variant_name }}({% if variant.has_fields() %}
                {% for field in variant.fields() -%}
                {{ field|read_fn(config) }}(buf){% if loop.last %}{% else %},{% endif %}
                {% endfor -%}
            {%- endif -%});
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
        {%- endif %}
    }

    @Override
    public long allocationSize({{ type_name }} value) {
        {%- if e.is_flat() %}
        return 4L;
        {%- else %}
        return switch(value) {
            {%- for variant in e.variants() %}
            case {{ type_name }}.{{ variant|error_variant_name }} x -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4L
                {%- for field in variant.fields() %}
                + {{ field|allocation_size_fn(config) }}(x.{% call java::field_name(field, loop.index) %})
                {%- endfor %}
            );
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
        {%- endif %}
    }

    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
        switch(value) {
            {%- for variant in e.variants() %}
            case {{ type_name }}.{{ variant|error_variant_name }} x -> {
                buf.putInt({{ loop.index }});
                {%- for field in variant.fields() %}
                {{ field|write_fn(config) }}(x.{% call java::field_name(field, loop.index) %}, buf);
                {%- endfor %}
            }
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
    }
}
