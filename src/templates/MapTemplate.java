{%- let key_type_name = key_type|type_name(ci, config) %}
{%- let value_type_name = value_type|type_name(ci, config) %}
package {{ config.package_name() }};

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<Map<{{ key_type_name }}, {{ value_type_name }}>> {
    INSTANCE;

    @Override
    public Map<{{ key_type_name }}, {{ value_type_name }}> read(ByteBuffer buf) {
        int len = buf.getInt();
        return IntStream.range(0, len).boxed().collect(Collectors.toMap(
            _x -> {{ key_type|read_fn(config) }}(buf),
            _x -> {{ value_type|read_fn(config) }}(buf)
        ));
    }

    @Override
    public long allocationSize(Map<{{ key_type_name }}, {{ value_type_name }}> value) {
        long spaceForMapSize = 4;
        long spaceForChildren = value.entrySet().stream().mapToLong(entry ->
            {{ key_type|allocation_size_fn(config) }}(entry.getKey()) +
            {{ value_type|allocation_size_fn(config) }}(entry.getValue())
        ).sum();
        return spaceForMapSize + spaceForChildren;
    }

    @Override
    public void write(Map<{{ key_type_name }}, {{ value_type_name }}> value, ByteBuffer buf) {
        buf.putInt(value.size());
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        for (var entry : value.entrySet()) {
            {{ key_type|write_fn(config) }}(entry.getKey(), buf);
            {{ value_type|write_fn(config) }}(entry.getValue(), buf);
        }
    }
}
