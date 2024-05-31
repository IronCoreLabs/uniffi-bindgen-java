{%- let package_name = config.package_name() %}
{%- match config.custom_types.get(name.as_str())  %}
{%- when None %}
{#- Define the type using typealiases to the builtin #}
{%- let ffi_type_name=builtin|ffi_type|ffi_type_name_by_value %}

 package {{ package_name }};

// Newtype
public record {{ type_name }}(
  {{ builtin|type_name(ci) }} value
) {
}

package {{ package_name }};
import java.nio.ByteBuffer;

public enum {{ ffi_converter_name }} implements FfiConverter<{{ type_name }}, {{ ffi_type_name}}> {
  INSTANCE;
  @Override
  public {{ type_name }} lift({{ ffi_type_name }} value) {
      var builtinValue = {{ builtin|lift_fn }}(value);
      return new {{ type_name }}(builtinValue);
  }
  @Override
  public {{ ffi_type_name }} lower({{ type_name }} value) {
      var builtinValue = value.value();
      return {{ builtin|lower_fn }}(builtinValue);
  }
  @Override
  public {{ type_name }} read(ByteBuffer buf) {
      var builtinValue = {{ builtin|read_fn }}(buf);
      return new {{ type_name }}(builtinValue);
  }
  @Override
  public long allocationSize({{ type_name }} value) {
      var builtinValue = value.value();
      return {{ builtin|allocation_size_fn }}(builtinValue);
  }
  @Override
  public void write({{ type_name }} value, ByteBuffer buf) {
      var builtinValue = value.value();
      {{ builtin|write_fn }}(builtinValue, buf);
  }
}

{%- when Some with (config) %}

{%- let ffi_type_name=builtin|ffi_type|ffi_type_name_by_value %}

{# When the config specifies a different type name, create a typealias for it #}
{%- match config.type_name %}
{%- when Some(concrete_type_name) %}

package {{ package_name }};

{%- match config.imports %}
{%- when Some(imports) %}
{%- for import_name in imports %}
import {{ import_name }};
{%- endfor %}
{%- else %}
{%- endmatch %}

public record {{ type_name }}(
  {{ concrete_type_name }} value
) {}

{%- else %}
{%- endmatch %}

package {{ package_name }};
import java.nio.ByteBuffer;

{%- match config.imports %}
{%- when Some(imports) %}
{%- for import_name in imports %}
import {{ import_name }};
{%- endfor %}
{%- else %}
{%- endmatch %}
// FFI converter with custom code.
public enum {{ ffi_converter_name }} implements FfiConverter<{{ type_name }}, {{ ffi_type_name }}> {
    INSTANCE;
    @Override
    public {{ type_name }} lift({{ ffi_type_name }} value) {
        var builtinValue = {{ builtin|lift_fn }}(value);
        try{
          return new {{ type_name}}({{ config.into_custom.render("builtinValue") }});
        } catch(Exception e){
          throw new RuntimeException(e);
        }
    }
    @Override
    public {{ ffi_type_name }} lower({{ type_name }} value) {
      try{
        var builtinValue = {{ config.from_custom.render("value.value()") }};
        return {{ builtin|lower_fn }}(builtinValue);
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
    @Override
    public {{ type_name }} read(ByteBuffer buf) {
      try{
        var builtinValue = {{ builtin|read_fn }}(buf);
        return new {{ type_name }}({{ config.into_custom.render("builtinValue") }});
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
    @Override
    public long allocationSize({{ type_name }} value) {
      try {
        var builtinValue = {{ config.from_custom.render("value.value()") }};
        return {{ builtin|allocation_size_fn }}(builtinValue);
      } catch(Exception e){
        throw new RuntimeException(e);
      } 
    }
    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
      try {
        var builtinValue = {{ config.from_custom.render("value.value()") }};
        {{ builtin|write_fn }}(builtinValue, buf);
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
}
{%- endmatch %}
