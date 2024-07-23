{%- let package_name = config.package_name() %}
{%- let ffi_type_name=builtin|ffi_type|ffi_type_name_by_value(config) %}
{%- match config.custom_types.get(name.as_str())  %}
{%- when None %}
{#- Define a newtype record that delegates to the builtin #}

package {{ package_name }};

import java.util.List;
import java.util.Map;

public record {{ type_name }}(
  {{ builtin|type_name(ci, config) }} value
) {
}

package {{ package_name }};

import java.nio.ByteBuffer;
import com.sun.jna.Pointer;

public enum {{ ffi_converter_name }} implements FfiConverter<{{ type_name }}, {{ ffi_type_name}}> {
  INSTANCE;
  @Override
  public {{ type_name }} lift({{ ffi_type_name }} value) {
      var builtinValue = {{ builtin|lift_fn(config) }}(value);
      return new {{ type_name }}(builtinValue);
  }
  @Override
  public {{ ffi_type_name }} lower({{ type_name }} value) {
      var builtinValue = value.value();
      return {{ builtin|lower_fn(config) }}(builtinValue);
  }
  @Override
  public {{ type_name }} read(ByteBuffer buf) {
      var builtinValue = {{ builtin|read_fn(config) }}(buf);
      return new {{ type_name }}(builtinValue);
  }
  @Override
  public long allocationSize({{ type_name }} value) {
      var builtinValue = value.value();
      return {{ builtin|allocation_size_fn(config) }}(builtinValue);
  }
  @Override
  public void write({{ type_name }} value, ByteBuffer buf) {
      var builtinValue = value.value();
      {{ builtin|write_fn(config) }}(builtinValue, buf);
  }
}

{%- when Some with (custom_type_config) %}

{# 
  When the config specifies a different type name, use that other type inside our newtype.
  Lift/lower using their configured code.
#}
{%- match custom_type_config.type_name %}
{%- when Some(concrete_type_name) %}

package {{ package_name }};

{%- match custom_type_config.imports %}
{%- when Some(imports) %}
{%- for import_name in imports %}
import {{ import_name }};
{%- endfor %}
{%- else %}
{%- endmatch %}
import java.util.List;
import java.util.Map;

public record {{ type_name }}(
  {{ concrete_type_name }} value
) {}

{%- else %}
{%- endmatch %}

package {{ package_name }};

import java.nio.ByteBuffer;
import com.sun.jna.Pointer;

{%- match custom_type_config.imports %}
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
        var builtinValue = {{ builtin|lift_fn(config) }}(value);
        try{
          return new {{ type_name}}({{ custom_type_config.into_custom.render("builtinValue") }});
        } catch(Exception e){
          throw new RuntimeException(e);
        }
    }
    @Override
    public {{ ffi_type_name }} lower({{ type_name }} value) {
      try{
        var builtinValue = {{ custom_type_config.from_custom.render("value.value()") }};
        return {{ builtin|lower_fn(config) }}(builtinValue);
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
    @Override
    public {{ type_name }} read(ByteBuffer buf) {
      try{
        var builtinValue = {{ builtin|read_fn(config) }}(buf);
        return new {{ type_name }}({{ custom_type_config.into_custom.render("builtinValue") }});
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
    @Override
    public long allocationSize({{ type_name }} value) {
      try {
        var builtinValue = {{ custom_type_config.from_custom.render("value.value()") }};
        return {{ builtin|allocation_size_fn(config) }}(builtinValue);
      } catch(Exception e){
        throw new RuntimeException(e);
      } 
    }
    @Override
    public void write({{ type_name }} value, ByteBuffer buf) {
      try {
        var builtinValue = {{ custom_type_config.from_custom.render("value.value()") }};
        {{ builtin|write_fn(config) }}(builtinValue, buf);
      } catch(Exception e){
        throw new RuntimeException(e);
      }
    }
}
{%- endmatch %}
