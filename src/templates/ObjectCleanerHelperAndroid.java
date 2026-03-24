package {{ config.package_name() }};

import android.os.Build;
import androidx.annotation.RequiresApi;

// The SystemCleaner, available from API Level 33.
// Some API Level 33 OSes do not support using it, so we require API Level 34.
class AndroidSystemCleaner implements UniffiCleaner {
    private final java.lang.ref.Cleaner cleaner;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    AndroidSystemCleaner() {
      this.cleaner = android.system.SystemCleaner.cleaner();
    }

    @Override
    public UniffiCleaner.Cleanable register(java.lang.Object value, java.lang.Runnable cleanUpTask) {
        return new AndroidSystemCleanable(cleaner.register(value, cleanUpTask));
    }
}

package {{ config.package_name() }};

import android.os.Build;
import androidx.annotation.RequiresApi;

class AndroidSystemCleanable implements UniffiCleaner.Cleanable {
    private final java.lang.ref.Cleaner.Cleanable cleanable;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    AndroidSystemCleanable(java.lang.ref.Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }

    @Override
    public void clean() {
      cleanable.clean();
    }
}
