{%- import "macros.java" as java %}

package {{ config.package_name() }};

import java.util.stream.Stream;

public interface Disposable {
    void destroy();

    static void destroy(Object... args) {
        Stream.of(args)
              .filter(Disposable.class::isInstance)
              .map(Disposable.class::cast)
              .forEach(disposable -> {disposable.destroy();} );
    }
}

package {{ config.package_name() }};

public class DisposableHelper {
    public static <T extends Disposable, R> R use(T disposable, java.util.function.Function<T, R> block) {
        try {
            return block.apply(disposable);
        } finally {
            try {
                if (disposable != null) {
                    disposable.destroy();
                }
            } catch (Throwable ignored) {
                // swallow
            }
        }
    }
}

package {{ config.package_name() }};

public class NoPointer {
    // Private constructor to prevent instantiation
    private NoPointer() {}

    // Static final instance of the class so it can be used in tests
    public static final NoPointer INSTANCE = new NoPointer();
}

{%- for type_ in ci.iter_types() %}
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
{%- let ffi_converter_instance = type_|ffi_converter_instance %}
{%- let canonical_type_name = type_|canonical_name %}
{%- let contains_object_references = ci.item_contains_object_references(type_) %}

{#
 # Map `Type` instances to an include statement for that type.
 #
 # There is a companion match in `JavaCodeOracle::create_code_type()` which performs a similar function for the
 # Rust code.
 #
 #   - When adding additional types here, make sure to also add a match arm to that function.
 #   - To keep things manageable, let's try to limit ourselves to these 2 mega-matches
 #}
{%- match type_ %}

{%- when Type::Boolean %}
{%- include "BooleanHelper.java" %}

{%- when Type::Custom { module_path, name, builtin } %}
{% include "CustomTypeTemplate.java" %}

{%- when Type::String %}
{%- include "StringHelper.java" %}

{%- when Type::Enum { name, module_path } %}
{%- let e = ci.get_enum_definition(name).unwrap() %}
{%- if !ci.is_name_used_as_error(name) %}
{% include "EnumTemplate.java" %}
{%- else %}
{% include "ErrorTemplate.java" %}
{%- endif -%}

{%- when Type::Duration %}
{% include "DurationHelper.java" %}

{%- when Type::Int64 or Type::UInt64 %}
{%- include "Int64Helper.java" %}

{%- when Type::Int8 or Type::UInt8 %}
{%- include "Int8Helper.java" %}

{%- when Type::Int16 or Type::UInt16 %}
{%- include "Int16Helper.java" %}

{%- when Type::Int32 or Type::UInt32 %}
{%- include "Int32Helper.java" %}

{%- when Type::Float32 %}
{%- include "Float32Helper.java" %}

{%- when Type::Float64 %}
{%- include "Float64Helper.java" %}

{%- when Type::Map { key_type, value_type } %}
{% include "MapTemplate.java" %}

{%- when Type::Object { module_path, name, imp } %}
{% include "ObjectTemplate.java" %}

{%- when Type::Optional { inner_type } %}
{% include "OptionalTemplate.java" %}

{%- when Type::Record { name, module_path } %}
{% include "RecordTemplate.java" %}

{%- when Type::Sequence { inner_type } %}
{% include "SequenceTemplate.java" %}

{%- when Type::Timestamp %}
{% include "TimestampHelper.java" %}

{# TODO(murph): implement the rest of the types

{%- when Type::Bytes %}
{%- include "ByteArrayHelper.kt" %}

{%- when Type::CallbackInterface { module_path, name } %}
{% include "CallbackInterfaceTemplate.kt" %}

{%- when Type::External { module_path, name, namespace, kind, tagged } %}
{% include "ExternalTypeTemplate.kt" %}
#}
{%- else %}
{%- endmatch %}
{%- endfor %}

{# TODO(murph): async
{%- if ci.has_async_fns() %}
{# Import types needed for async support #}
{{ self.add_import("kotlin.coroutines.resume") }}
{{ self.add_import("kotlinx.coroutines.launch") }}
{{ self.add_import("kotlinx.coroutines.suspendCancellableCoroutine") }}
{{ self.add_import("kotlinx.coroutines.CancellableContinuation") }}
{{ self.add_import("kotlinx.coroutines.DelicateCoroutinesApi") }}
{{ self.add_import("kotlinx.coroutines.Job") }}
{{ self.add_import("kotlinx.coroutines.GlobalScope") }}
{%- endif %}
#}
