{%- let inner_type_name = inner_type|type_name(ci, config) %}
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package {{ config.package_name() }};

public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<java.util.List<{{ inner_type_name }}>> {
  INSTANCE;

  @Override
  public java.util.List<{{ inner_type_name }}> read(java.nio.ByteBuffer buf) {
    int len = buf.getInt();
    return java.util.stream.IntStream.range(0, len).mapToObj(_i -> {{ inner_type|read_fn(config, ci) }}(buf)).toList();
  }

  @Override
  public long allocationSize(java.util.List<{{ inner_type_name }}> value) {
    long sizeForLength = 4L;
    long sizeForItems = value.stream().mapToLong(inner -> {{ inner_type|allocation_size_fn(config, ci) }}(inner)).sum();
    return sizeForLength + sizeForItems;
  }

  @Override
  public void write(java.util.List<{{ inner_type_name }}> value, java.nio.ByteBuffer buf) {
    buf.putInt(value.size());
    value.forEach(inner -> {{ inner_type|write_fn(config, ci) }}(inner, buf));
  }
}
