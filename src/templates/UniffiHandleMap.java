package {{ config.package_name() }};

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

// Map handles to objects
//
// This is used pass an opaque 64-bit handle representing a foreign object to the Rust code.
class UniffiHandleMap<T extends Object> {
    private ConcurrentHashMap<Long, T> map = new ConcurrentHashMap<>();
    private AtomicLong counter = new AtomicLong(0);

    public int size() {
        return map.size();
    }

    // Insert a new object into the handle map and get a handle for it
    public long insert(T obj) {
        var handle = counter.getAndAdd(1);
        map.put(handle, obj);
        return handle;
    }

    // Get an object from the handle map
    public T get(long handle) {
        var entry = map.get(handle);
        if (entry == null) {
            throw new InternalException("UniffiHandleMap.get: Invalid handle");
        } else {
            return entry;
        }   
    }

    // Remove an entry from the handlemap and get the Java object back
    public T remove(long handle) {
        var entry = map.remove(handle);
        if (entry == null) {
            throw new InternalException("UniffiHandleMap.get: Invalid handle");
        } else {
            return entry;
        }   
    }
}

