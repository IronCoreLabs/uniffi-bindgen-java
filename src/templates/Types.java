{%- import "macros.java" as java %}
// TODO(murph): need a single point to include all the wrapper-y and macro code once if possible, relying on it all
// being in the same java package to make it available

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

// TODO(murph): in the Kotlin code this was an inline function `T.use(block: (T) -> R)`
//              Will likely need to see where it's being called and make sure this is called instead
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

{%- for type_ in ci.iter_types() %}
{%- let type_name = type_|type_name(ci) %}
{%- let ffi_converter_name = type_|ffi_converter_name %}
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

{%- when Type::String %}
{%- include "StringHelper.java" %}

{# TODO(murph): implement the rest of the types
{%- when Type::Int8 %}
{%- include "Int8Helper.kt" %}

{%- when Type::Int16 %}
{%- include "Int16Helper.kt" %}

{%- when Type::Int32 %}
{%- include "Int32Helper.kt" %}

{%- when Type::Int64 %}
{%- include "Int64Helper.kt" %}

{%- when Type::UInt8 %}
{%- include "UInt8Helper.kt" %}

{%- when Type::UInt16 %}
{%- include "UInt16Helper.kt" %}

{%- when Type::UInt32 %}
{%- include "UInt32Helper.kt" %}

{%- when Type::UInt64 %}
{%- include "UInt64Helper.kt" %}

{%- when Type::Float32 %}
{%- include "Float32Helper.kt" %}

{%- when Type::Float64 %}
{%- include "Float64Helper.kt" %}

{%- when Type::Bytes %}
{%- include "ByteArrayHelper.kt" %}

{%- when Type::Enum { name, module_path } %}
{%- let e = ci.get_enum_definition(name).unwrap() %}
{%- if !ci.is_name_used_as_error(name) %}
{% include "EnumTemplate.kt" %}
{%- else %}
{% include "ErrorTemplate.kt" %}
{%- endif -%}

{%- when Type::Object { module_path, name, imp } %}
{% include "ObjectTemplate.kt" %}

{%- when Type::Record { name, module_path } %}
{% include "RecordTemplate.kt" %}

{%- when Type::Optional { inner_type } %}
{% include "OptionalTemplate.kt" %}

{%- when Type::Sequence { inner_type } %}
{% include "SequenceTemplate.kt" %}

{%- when Type::Map { key_type, value_type } %}
{% include "MapTemplate.kt" %}

{%- when Type::CallbackInterface { module_path, name } %}
{% include "CallbackInterfaceTemplate.kt" %}

{%- when Type::Timestamp %}
{% include "TimestampHelper.kt" %}

{%- when Type::Duration %}
{% include "DurationHelper.kt" %}

{%- when Type::Custom { module_path, name, builtin } %}
{% include "CustomTypeTemplate.kt" %}

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
