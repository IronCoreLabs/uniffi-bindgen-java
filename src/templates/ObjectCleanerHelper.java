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

    public static UniffiCleaner create() {
        {% if config.android_cleaner() %}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return new AndroidSystemCleaner();
        } else {
            return new UniffiJnaCleaner();
        }
        {%- else %}
        try {
            // For safety's sake: if the library hasn't been run in android_cleaner = true
            // mode, but is being run on Android, then we still need to think about
            // Android API versions.
            // So we check if java.lang.ref.Cleaner is there, and use that…
            Class.forName("java.lang.ref.Cleaner");
            return new JavaLangRefCleaner();
        } catch (ClassNotFoundException e) {
            // … otherwise, fallback to the JNA cleaner.
            return new UniffiJnaCleaner();
        }
        {%- endif %}
    }
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
{% if config.android_cleaner() %}
{%-   include "ObjectCleanerHelperAndroid.java" %}
{%- else %}
{%-   include "ObjectCleanerHelperJvm.java" %}
{%- endif %}

