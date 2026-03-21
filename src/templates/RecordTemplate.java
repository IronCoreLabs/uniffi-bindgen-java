{%- let rec = ci.get_record_definition(name).unwrap() %}
{%- let uniffi_trait_methods = rec.uniffi_trait_methods() %}
package {{ config.package_name() }};

{%- call java::docstring(rec, 0) %}
{%- if rec.has_fields() %}
{%- if config.generate_immutable_records() %}
public record {{ type_name }}(
    {%- for field in rec.fields() %}
    {%- call java::docstring(field, 4) %}
    {{ field|type_name(ci, config) }} {{ field.name()|var_name -}}
    {% if !loop.last %}, {% endif %}
    {%- endfor %}
) {% if contains_object_references %}implements AutoCloseable{% endif %}{% if uniffi_trait_methods.ord_cmp.is_some() %}{% if contains_object_references %}, {% else %}implements {% endif %}Comparable<{{ type_name }}>{% endif %} {
    {% if contains_object_references %}
    @Override
    public void close() {
        {% call java::destroy_fields(rec) %}
    }
    {% endif %}
    {% for meth in rec.methods() -%}
    {%- call java::func_decl("public", "", meth, 4) %}
    {% endfor %}
    {# Add trait implementations for immutable records - these override record's auto-generated methods #}
    {% call java::uniffi_trait_impls(uniffi_trait_methods, type_name) %}
}
{% else %}
public class {{ type_name }} {% if contains_object_references %}implements AutoCloseable{% if uniffi_trait_methods.ord_cmp.is_some() %}, Comparable<{{ type_name }}>{% endif %}{% else %}{% if uniffi_trait_methods.ord_cmp.is_some() %}implements Comparable<{{ type_name }}> {% endif %}{% endif %}{
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

    {# Use trait-based implementations if available, otherwise use hardcoded #}
    {%- if uniffi_trait_methods.eq_eq.is_none() %}
    @Override
    public boolean equals(java.lang.Object other) {
        if (other instanceof {{ type_name }}) {
            {{ type_name }} t = ({{ type_name }}) other;
            return ({% for field in rec.fields() %}{% let field_var_name = field.name()|var_name %}
              {#- currently all primitives are already referenced by their boxed values in generated code, so `.equals` works for everything #}
              java.util.Objects.equals({{ field_var_name }}, t.{{ field_var_name }}){% if !loop.last%} && {% endif %}
              {% endfor %}
            );
        };
        return false;
    }
    {%- endif %}

    {%- if uniffi_trait_methods.hash_hash.is_none() %}
    @Override
    public int hashCode() {
        return java.util.Objects.hash({% for field in rec.fields() %}{{ field.name()|var_name }}{% if !loop.last%}, {% endif %}{% endfor %});
    }
    {%- endif %}

    {% for meth in rec.methods() -%}
    {%- call java::func_decl("public", "", meth, 4) %}
    {% endfor %}
    {# Add trait implementations #}
    {% call java::uniffi_trait_impls(uniffi_trait_methods, type_name) %}
}
{% endif %}
{%- else %}
public class {{ type_name }}{% if uniffi_trait_methods.ord_cmp.is_some() %} implements Comparable<{{ type_name }}>{% endif %} {
    {%- if uniffi_trait_methods.eq_eq.is_none() %}
    @Override
    public boolean equals(java.lang.Object other) {
        return other instanceof {{ type_name }};
    }
    {%- endif %}

    {%- if uniffi_trait_methods.hash_hash.is_none() %}
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
    {%- endif %}

    {% for meth in rec.methods() -%}
    {%- call java::func_decl("public", "", meth, 4) %}
    {% endfor %}
    {# Add trait implementations #}
    {% call java::uniffi_trait_impls(uniffi_trait_methods, type_name) %}
}
{%- endif %}

package {{ config.package_name() }};

public enum {{ rec|ffi_converter_name }} implements FfiConverterRustBuffer<{{ type_name }}> {
  INSTANCE;

  @Override
  public {{ type_name }} read(java.nio.ByteBuffer buf) {
    {%- if rec.has_fields() %}
    return new {{ type_name }}(
    {%- for field in rec.fields() %}
      {{ field|read_fn(config, ci) }}(buf){% if !loop.last %},{% else %}{% endif %}
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
            {{ field|allocation_size_fn(config, ci) }}(value.{{ field.name()|var_name }}()){% if !loop.last %} +{% endif %}
        {%- endfor %}
      ); 
      {%- else %}
      return 0L;
      {%- endif %}
  }

  @Override
  public void write({{ type_name }} value, java.nio.ByteBuffer buf) {
    {%- for field in rec.fields() %}
      {{ field|write_fn(config, ci) }}(value.{{ field.name()|var_name }}(), buf);
    {%- endfor %}
  }
}
