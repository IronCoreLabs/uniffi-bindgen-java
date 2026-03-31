{%- let namespace = ci.namespace_for_module_path(module_path)? %}
{%- let external_package_name = self.external_type_package_name(module_path, namespace) %}
{%- let class_name = name|class_name(ci) %}

{%- if ci.is_name_used_as_error(name) %}
package {{ config.package_name() }};

public class {{ class_name }}ExternalErrorHandler implements UniffiRustCallStatusErrorHandler<{{ external_package_name }}.{{ class_name }}> {
    @Override
    public {{ external_package_name }}.{{ class_name }} lift(java.lang.foreign.MemorySegment errorBuf) {
        // In FFM, RustBuffer is already a java.lang.foreign.MemorySegment — pass directly to external package
        return new {{ external_package_name }}.{{ class_name }}ErrorHandler().lift(errorBuf);
    }
}
{%- endif %}
