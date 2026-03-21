{%- let key_type_name = key_type|type_name(ci, config) %}
{%- let value_type_name = value_type|type_name(ci, config) %}
package {{ config.package_name() }};

public enum {{ ffi_converter_name }} implements FfiConverterRustBuffer<java.util.Map<{{ key_type_name }}, {{ value_type_name }}>> {
    INSTANCE;

    @Override
    public java.util.Map<{{ key_type_name }}, {{ value_type_name }}> read(java.nio.ByteBuffer buf) {
        int len = buf.getInt();
        // Collectors.toMap would be preferred here, but theres a bug that doesn't allow
        // null values in the map, even though that is valid Java
        return java.util.stream.IntStream.range(0, len).boxed().collect(
            java.util.HashMap::new,
            (m, _v) -> m.put(
                {{ key_type|read_fn(config, ci) }}(buf),
                {{ value_type|read_fn(config, ci) }}(buf)
            ),
            java.util.HashMap::putAll
        );
    }

    @Override
    public long allocationSize(java.util.Map<{{ key_type_name }}, {{ value_type_name }}> value) {
        long spaceForMapSize = 4;
        long spaceForChildren = value.entrySet().stream().mapToLong(entry ->
            {{ key_type|allocation_size_fn(config, ci) }}(entry.getKey()) +
            {{ value_type|allocation_size_fn(config, ci) }}(entry.getValue())
        ).sum();
        return spaceForMapSize + spaceForChildren;
    }

    @Override
    public void write(java.util.Map<{{ key_type_name }}, {{ value_type_name }}> value, java.nio.ByteBuffer buf) {
        buf.putInt(value.size());
        // The parens on `(k, v)` here ensure we're calling the right method,
        // which is important for compatibility with older android devices.
        // Ref https://blog.danlew.net/2017/03/16/kotlin-puzzler-whose-line-is-it-anyways/
        for (var entry : value.entrySet()) {
            {{ key_type|write_fn(config, ci) }}(entry.getKey(), buf);
            {{ value_type|write_fn(config, ci) }}(entry.getValue(), buf);
        }
    }
}
