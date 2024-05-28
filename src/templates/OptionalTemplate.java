{%- let inner_type_name = inner_type|type_name(ci) %}
package {{ config.package_name() }};

import java.nio.ByteBuffer;

// public class TestForOptionals {}

{#- 
  Swift, Python, and Go bindings all use nullable/nilable optionals instead of `Optional` types where they exist.
  Kotlin and C# bindings use their type system to directly express nullability with `?`.
  Java has an `Optional` type that some FP Java folks use, but we'll lean on the more straightforward Java way here
  and have it be invisibly nullable, because that's the normal Java way.
#}
public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<{{ inner_type_name }}> {
  INSTANCE;

  @Override
  public {{ inner_type_name }} read(ByteBuffer buf) {
    if (buf.get() == (byte)0) {
      return null;
    }
    return {{ inner_type|read_fn }}(buf);
  }

  @Override
  public long allocationSize({{ inner_type_name }} value) {
    if (value == null) {
      return 1L;
    } else {
      return 1L + {{ inner_type|allocation_size_fn }}(value);
    }
  }

  @Override
  public void write({{ inner_type_name }} value, ByteBuffer buf) {
    if (value == null) {
      buf.put((byte)0);
    } else {
      buf.put((byte)1);
      {{ inner_type|write_fn }}(value, buf);
    }
  }
}
