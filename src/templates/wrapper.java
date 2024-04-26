// This file was autogenerated by some hot garbage in the `uniffi` crate.
// Trust me, you don't want to mess with it!

// TODO(murph): where to put this when generated
{%- call java::docstring_value(ci.namespace_docstring(), 0) %}

// Common helper code.
//
// Ideally this would live in a separate .java file where it can be unittested etc
// in isolation, and perhaps even published as a re-useable package.
//
// However, it's important that the details of how this helper code works (e.g. the
// way that different builtin types are passed across the FFI) exactly match what's
// expected by the Rust code on the other side of the interface. In practice right
// now that means coming from the exact some version of `uniffi` that was used to
// compile the Rust component. The easiest way to ensure this is to bundle the Java
// helpers directly inline like we're doing here.

{%- for req in self.imports() %}
{{ req.render() }}
{%- endfor %}

{% include "RustBufferTemplate.java" %}
{% include "FfiConverterTemplate.java" %}
{% include "Helpers.java" %}
{% include "HandleMap.java" %}

// Contains loading, initialization code,
// and the FFI Function declarations in a com.sun.jna.Library.
{% include "NamespaceLibraryTemplate.java" %}

// Async support
{#
{%- if ci.has_async_fns() %}
{% include "Async.java" %}
{%- endif %}
#}

// Public interface members begin here.
{{ type_helper_code }}

{#
{%- for func in ci.function_definitions() %}
{%- include "TopLevelFunction.java" %}
{%- endfor %}
#}

{% import "macros.java" as java %}
