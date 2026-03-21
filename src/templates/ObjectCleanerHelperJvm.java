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
