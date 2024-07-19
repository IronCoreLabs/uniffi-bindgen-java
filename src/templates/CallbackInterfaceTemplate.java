{%- let cbi = ci|get_callback_interface_definition(name) %}
{%- let ffi_init_callback = cbi.ffi_init_callback() %}
{%- let interface_name = cbi|type_name(ci, config) %}
{%- let interface_docstring = cbi.docstring() %}
{%- let methods = cbi.methods() %}
{%- let vtable = cbi.vtable() %}
{%- let vtable_methods = cbi.vtable_methods() %}

{% include "Interface.java" %}
{% include "CallbackInterfaceImpl.java" %}

package {{ config.package_name() }};

// The ffiConverter which transforms the Callbacks in to handles to pass to Rust.
public final class {{ ffi_converter_name }} extends FfiConverterCallbackInterface<{{ interface_name }}> {
  static final {{ ffi_converter_name }} INSTANCE = new {{ ffi_converter_name }}();

  private {{ ffi_converter_name }}() {}
}

