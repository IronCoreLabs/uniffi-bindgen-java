{%- let inner_type_name = inner_type|type_name(ci, config) %}
/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package {{ config.package_name() }};

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<List<{{ inner_type_name }}>> {
  INSTANCE;

  @Override
  public List<{{ inner_type_name }}> read(ByteBuffer buf) {
    int len = buf.getInt();
    List<{{ inner_type_name }}> list = new ArrayList<>(len);
    for (int i = 0; i < len; i++) {
      list.add({{ inner_type|read_fn(config, ci) }}(buf));
    }
    return list;
  }

  @Override
  public long allocationSize(List<{{ inner_type_name }}> value) {
    long sizeForLength = 4L;
    long sizeForItems = value.stream().mapToLong(inner -> {{ inner_type|allocation_size_fn(config, ci) }}(inner)).sum();
    return sizeForLength + sizeForItems;
  }

  @Override
  public void write(List<{{ inner_type_name }}> value, ByteBuffer buf) {
    buf.putInt(value.size());
    value.forEach(inner -> {{ inner_type|write_fn(config, ci) }}(inner, buf));
  }
}
