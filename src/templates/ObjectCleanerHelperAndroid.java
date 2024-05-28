package {{ config.package_name() }};

import android.os.Build;
import androidx.annotation.RequiresApi;
import java.lang.ref.Cleaner;

// The SystemCleaner, available from API Level 33.
// Some API Level 33 OSes do not support using it, so we require API Level 34.
class AndroidSystemCleaner implements UniffiCleaner {
    private final Cleaner cleaner;

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    AndroidSystemCleaner() {
      this.cleaner = android.system.SystemCleaner.cleaner();
    }

    @Override
    public UniffiCleaner.Cleanable register(Object value, Runnable cleanUpTask) {
        return new AndroidSystemCleanable(cleaner.register(value, cleanUpTask));
    }
}

package {{ config.package_name() }};

import android.os.Build;
import androidx.annotation.RequiresApi;
import java.lang.ref.Cleaner;

class AndroidSystemCleanable implements UniffiCleaner.Cleanable {
    private final Cleaner.Cleanable cleanable;
    
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    AndroidSystemCleanable(Cleaner.Cleanable cleanable) {
        this.cleanable = cleanable;
    }
    
    @Override
    public void clean() {
      cleanable.clean();
    }
}
