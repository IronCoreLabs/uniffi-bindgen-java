package {{ config.package_name() }};

{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_instance = type_|ffi_converter_instance %}
{%- let canonical_type_name = type_|canonical_name %}

{% if e.is_flat() %}
{%- call java::docstring(e, 0) %}
public class {{ type_name }} extends Exception{% if contains_object_references %} Disposable {% endif %} {
    private {{ type_name }}(String message) {
      super(message);
    }

    {% for variant in e.variants() -%}
    {%- call java::docstring(variant, 4) %}
    {# TODO(murph): there should be two fields we're adding to this variant in the arithmetic example #}
    public static class {{ variant|error_variant_name }} extends {{ type_name }} {
      public {{ variant|error_variant_name }}(String message) {
        super(message);
      }
    }
    {% endfor %}

}

package {{ config.package_name() }};
{# TODO(murph): does this actually work? -#}
public class {{ type_name }}ErrorHandler implements UniffiRustCallStatusErrorHandler<{{ type_name }}> {
  @Override
  public {{ type_name }} lift(RustBuffer.ByValue errorBuf){
     return {{ ffi_converter_instance }}.lift(errorBuf);
  }
}

{%- else %}
{%- call java::docstring(e, 0) %}
{# TODO(murph): interface can't extend Exception (class). Records can't be in a sealed class #}
sealed interface {{ type_name }} extends Exception{% if contains_object_references %}, Disposable {% endif %} {
    {% for variant in e.variants() -%}
    {%- call java::docstring(variant, 4) %}
    {%- let variant_name = variant|error_variant_name %}
    record {{ variant_name }}(
        {% for field in variant.fields() -%}
        {%- call java::docstring(field, 8) %}
        {{ field|type_name(ci) }} {{ field.name()|var_name }}{% if loop.last %}{% else %}, {% endif %}
        {% endfor -%}
    ) implements {{ type_name }} {
        @Override
        public String getMessage() {
          return "{%- for field in variant.fields() %}{{ field.name()|var_name|unquote }}=${ {{field.name()|var_name }} }{% if !loop.last %}, {% endif %}{% endfor %}";
        }
    }
    {% endfor %}
    
    {# TODO(murph): does this actually work? #}
    final class ErrorHandler implements UniffiRustCallStatusErrorHandler<{{ type_name }}> {
      @Override
      public {{ type_name }} lift(RustBuffer.ByValue errorBuf){
         return {{ ffi_converter_instance }}.lift(errorBuf);
      }
    }

    {% if contains_object_references %}
    @Override
    void destroy() {
        switch (this) {
            {%- for variant in e.variants() %}
            case {{ type_name }}.{{ variant|error_variant_name }} -> {
                {%- if variant.has_fields() %}
                {% call java::destroy_fields(variant) %}
                {% else -%}
                // Nothing to destroy
                {%- endif %}
            }
            {%- endfor %}
        };
    }
    {% endif %}
}

{%- endif %}

package {{ config.package_name() }};

import java.nio.ByteBuffer;

public enum {{ e|ffi_converter_name }} implements FfiConverterRustBuffer<{{ type_name }}> {
    INSTANCE;

    @Override
    public {{ type_name }} read(ByteBuffer buf) {
        {%- if e.is_flat() %}
        return switch(buf.getInt()) {
            {%- for variant in e.variants() %}
            case {{ loop.index }} -> new {{ type_name }}.{{ variant|error_variant_name }}({{ Type::String.borrow()|read_fn }}(buf));
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
        {%- else %}

        return switch(buf.getInt()) {
            {%- for variant in e.variants() %}
            case {{ loop.index }} -> {{ type_name }}.{{ variant|error_variant_name }}({% if variant.has_fields() %}
                {% for field in variant.fields() -%}
                {{ field|read_fn }}(buf){% if loop.last %}{% else %},{% endif %}
                {% endfor -%});
            {%- endif -%})
            {%- endfor %}
            default -> throw RuntimeException("invalid error enum value, something is very wrong!!");
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
            {# TODO(murph): `message` isn't showing up in the variant match, breaking it #}
            case {{ type_name }}.{{ variant|error_variant_name }} message {% for field in variant.fields() %} {{ field.name() }}{% endfor %} -> (
                // Add the size for the Int that specifies the variant plus the size needed for all fields
                4UL
                {%- for field in variant.fields() %}
                + {{ field|allocation_size_fn }}(value.{{ field.name()|var_name }})
                {%- endfor %};
            );
            {%- endfor %}
        };
        {%- endif %}
    }

    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
        switch(value) {
            {%- for variant in e.variants() %}
            case {{ type_name }}.{{ variant|error_variant_name }} message {% for field in variant.fields() %} {{ field.name() }}{% endfor %} -> {
                buf.putInt({{ loop.index }});
                {%- for field in variant.fields() %}
                {{ field|write_fn }}(value.{{ field.name()|var_name }}, buf);
                {%- endfor %}
            }
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
    }
}


