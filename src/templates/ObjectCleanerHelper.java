package {{ config.package_name() }};

// The cleaner interface for Object finalization code to run.
// With FFM we always use java.lang.ref.Cleaner (available on JVM and Android 9+).
interface UniffiCleaner {
    interface Cleanable {
        void clean();
    }

    UniffiCleaner.Cleanable register(java.lang.Object value, java.lang.Runnable cleanUpTask);

    public static UniffiCleaner create() {
        return new JavaLangRefCleaner();
    }
}

package {{ config.package_name() }};

class JavaLangRefCleaner implements UniffiCleaner {
    private final java.lang.ref.Cleaner cleaner;

    JavaLangRefCleaner() {
      this.cleaner = java.lang.ref.Cleaner.create();
    }

    @Override
    public UniffiCleaner.Cleanable register(java.lang.Object value, java.lang.Runnable cleanUpTask) {
        return new JavaLangRefCleanable(cleaner.register(value, cleanUpTask));
    }
}

package {{ config.package_name() }};

class JavaLangRefCleanable implements UniffiCleaner.Cleanable {
    private final java.lang.ref.Cleaner.Cleanable cleanable;

    JavaLangRefCleanable(java.lang.ref.Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }

    @Override
    public void clean() {
      cleanable.clean();
    }
}
