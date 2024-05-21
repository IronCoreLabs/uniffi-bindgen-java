{%- let inner_type_name = inner_type|type_name(ci) %}
package {{ config.package_name() }};
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

import java.nio.ByteBuffer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<List<{{ inner_type_name }}>> {
  @Override
  public List<{{ inner_type_name }}> read(ByteBuffer buf) {
    int len = buf.getInt();
    return IntStream.range(0, len).mapToObj(_i -> {{ inner_type|read_fn }}(buf)).toList();
  }

  @Override
  public Long allocationSize(List<{{ inner_type_name }}> value) {
    Long sizeForLength = 4L;
    Long sizeForItems = value.stream().map(inner -> {{ inner_type|allocation_size_fn }}(inner)).reduce(0, Long::sum);
    return sizeForLength + sizeForItems;
  }

  @Override
  public void write(List<{{ inner_type_name }}> value, ByteBuffer buf) {
    buf.putInt(value.size);
    value.forEach(inner -> {{ inner_type|write_fn }}(inner, buf));
  }
}
