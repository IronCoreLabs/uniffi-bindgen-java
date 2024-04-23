package {{ config.package_name() }};

// The cleaner interface for Object finalization code to run.
// This is the entry point to any implementation that we're using.
//
// The cleaner registers objects and returns cleanables, so now we are
// defining a `UniffiCleaner` with a `UniffiClenaer.Cleanable` to abstract the
// different implmentations available at compile time.
interface UniffiCleaner {
    interface Cleanable {
        void clean();
    }

    UniffiCleaner.Cleanable register(Object value, Runnable cleanUpTask);
}

package {{ config.package_name() }};

import com.sun.jna.internal.Cleaner;

// The fallback Jna cleaner, which is available for both Android, and the JVM.
class UniffiJnaCleaner implements UniffiCleaner {
    private final Cleaner cleaner = Cleaner.getCleaner();

    @Override
    public UniffiCleaner.Cleanable register(Object value, Runnable cleanUpTask) {
        return new UniffiJnaCleanable(cleaner.register(value, cleanUpTask));
    }
}

package {{ config.package_name() }};

import com.sun.jna.internal.Cleaner;

class UniffiJnaCleanable implements UniffiCleaner.Cleanable {
    private final Cleaner.Cleanable cleanable;

    public UniffiJnaCleanable(Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }

    @Override
    public void clean() {
        cleanable.clean();
    }
}

// We decide at uniffi binding generation time whether we were
// using Android or not.
// There are further runtime checks to chose the correct implementation
// of the cleaner.
{# TODO(murph): haven't filled this out yet, not sure how to do equivalent in java
{% if config.android_cleaner() %}
{%-   include "ObjectCleanerHelperAndroid.java" %}
{%- else %}
{%-   include "ObjectCleanerHelperJvm.java" %}
{%- endif %}
#}

