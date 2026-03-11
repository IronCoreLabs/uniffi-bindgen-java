package {{ config.package_name() }};

// The cleaner interface for Object finalization code to run.
// This is the entry point to any implementation that we're using.
//
// The cleaner registers objects and returns cleanables, so now we are
// defining a `UniffiCleaner` with a `UniffiCleaner.Cleanable` to abstract the
// different implementations available at compile time.
interface UniffiCleaner {
    interface Cleanable {
        void clean();
    }

    UniffiCleaner.Cleanable register(Object value, Runnable cleanUpTask);

    public static UniffiCleaner create() {
        return new JavaLangRefCleaner();
    }
}

{%- include "ObjectCleanerHelperJvm.java" %}
