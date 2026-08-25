{%- import "macros.java" as java %}

package {{ config.package_name() }};

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface AutoCloseableHelper {
    // Use java.lang.Object to avoid conflict with user-defined Object types
    static void close(java.lang.Object... args) {
        Stream
            .of(args)
            .forEach(obj -> {
                // this is all to avoid the problem reported in uniffi-rs#2467
                if (obj instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) obj).close();
                    } catch (java.lang.Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
                if (obj instanceof List<?>) {
                    for (int i = 0; i < ((List) obj).size(); i++) {
                        java.lang.Object element = ((List) obj).get(i);
                        if (element instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) element).close();
                            } catch (java.lang.Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
                if (obj instanceof Map<?, ?>) {
                    for (var value : ((Map) obj).values()) {
                        if (value instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) value).close();
                            } catch (java.lang.Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
                if (obj instanceof Iterable<?>) {
                    for (var value : ((Iterable) obj)) {
                        if (value instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) value).close();
                            } catch (java.lang.Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            });
    }
}
package {{ config.package_name() }};

public class NoHandle {
    // Private constructor to prevent instantiation
    private NoHandle() {}

    // Static final instance of the class so it can be used in tests
    public static final NoHandle INSTANCE = new NoHandle();
}

package {{ config.package_name() }};

// Marker class for constructors that accept a raw handle.
// This disambiguates constructor signatures when an interface has both
// a regular constructor and one accepting an FFI handle.
public class UniffiWithHandle {
    // Private constructor to prevent instantiation
    private UniffiWithHandle() {}

    public static final UniffiWithHandle INSTANCE = new UniffiWithHandle();
}

{#- Runtime support shared by every callback interface / object, so it is emitted once here
    rather than from inside the per-type templates. -#}
{%- if ci.has_callback_definitions() %}
{% include "CallbackInterfaceRuntime.java" %}
{%- endif %}

{%- if ci.has_object_definitions() %}
{% include "ObjectCleanerHelper.java" %}
{%- endif %}
