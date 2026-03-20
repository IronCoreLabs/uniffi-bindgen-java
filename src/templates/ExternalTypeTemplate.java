{%- let namespace = ci.namespace_for_module_path(module_path)? %}
{%- let external_package_name = self.external_type_package_name(module_path, namespace) %}
{%- let class_name = name|class_name(ci) %}

{%- if ci.is_name_used_as_error(name) %}
package {{ config.package_name() }};

public class {{ class_name }}ExternalErrorHandler implements UniffiRustCallStatusErrorHandler<{{ external_package_name }}.{{ class_name }}> {
    @Override
    public {{ external_package_name }}.{{ class_name }} lift(RustBuffer.ByValue errorBuf) {
        // Convert local RustBuffer to external package's RustBuffer
        {{ external_package_name }}.RustBuffer.ByValue externalBuf = new {{ external_package_name }}.RustBuffer.ByValue();
        externalBuf.capacity = errorBuf.capacity;
        externalBuf.len = errorBuf.len;
        externalBuf.data = errorBuf.data;
        return new {{ external_package_name }}.{{ class_name }}ErrorHandler().lift(externalBuf);
    }
}
{%- endif %}
