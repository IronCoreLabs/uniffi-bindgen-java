package {{ config.package_name() }};

{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_instance = type_|ffi_converter_instance %}
{%- let canonical_type_name = type_|canonical_name %}

{% if e.is_flat() %}
{%- call java::docstring(e, 0) %}
public sealed interface {{ type_name }} extends Exception{% if contains_object_references %}, Disposable {% endif %} {
    String message();

    {% for variant in e.variants() -%}
    {%- call java::docstring(variant, 4) %}
    record {{ variant|error_variant_name }}(String message) extends Exception(message) implements {{ type_name }}{}
    {% endfor %}

    {# TODO(murph): does this actually work? #}
    final class ErrorHandler implements UniffiRustCallStatusErrorHandler<{{ type_name }}> {
      @Override
      public {{ type_name }} lift(RustBuffer.ByValue errorBuf){
         return {{ ffi_converter_instance }}.lift(errorBuf);
      }
    }
}

{%- else %}
{%- call java::docstring(e, 0) %}
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
            case {{ loop.index }} -> {{ type_name }}.{{ variant|error_variant_name }}({{ Type::String.borrow()|read_fn }}(buf));
            {%- endfor %}
            default -> throw new RuntimeException("invalid error enum value, something is very wrong!!");
        };
        {%- else %}

        return switch(buf.getInt()) {
            {%- for variant in e.variants() %}
            case {{ loop.index }} -> {{ type_name }}.{{ variant|error_variant_name }}({% if variant.has_fields() %};
                {% for field in variant.fields() -%}
                {{ field|read_fn }}(buf){% if loop.last %}{% else %},{% endif %}
                {% endfor -%}
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
            case {{ type_name }}.{{ variant|error_variant_name }} -> (
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
            case {{ type_name }}.{{ variant|error_variant_name }} -> {
                buf.putInt({{ loop.index }});
                {%- for field in variant.fields() %}
                {{ field|write_fn }}(value.{{ field.name()|var_name }}, buf);
                {%- endfor %}
            }
            {%- endfor %}
        };
    }
}


