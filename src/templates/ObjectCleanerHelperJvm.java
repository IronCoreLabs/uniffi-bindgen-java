package {{ config.package_name() }};

import java.lang.ref.Cleaner;

class JavaLangRefCleaner implements UniffiCleaner {
    private final Cleaner cleaner;

    JavaLangRefCleaner() {
      this.cleaner = Cleaner.create();
    }

    @Override
    public UniffiCleaner.Cleanable register(Object value, Runnable cleanUpTask) {
        return new JavaLangRefCleanable(cleaner.register(value, cleanUpTask));
    }
}

package {{ config.package_name() }};

import java.lang.ref.Cleaner;

class JavaLangRefCleanable implements UniffiCleaner.Cleanable {
    private final Cleaner.Cleanable cleanable;
    
    JavaLangRefCleanable(Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }
    
    @Override
    public void clean() {
      cleanable.clean();
    }
}
