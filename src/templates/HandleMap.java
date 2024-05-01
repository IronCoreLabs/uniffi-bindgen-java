package {{ config.package_name() }};

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// This is used pass an opaque 64-bit handle representing a foreign object to the Rust code.
class UniffiHandleMap<T> {
    private final ConcurrentHashMap<Long, T> map = new ConcurrentHashMap<>();
    private final AtomicLong counter = new AtomicLong(0);

    public int getSize() {
        return map.size();
    }

    // Insert a new object into the handle map and get a handle for it
    public long insert(T obj) {
        long handle = counter.getAndAdd(1);
        map.put(handle, obj);
        return handle;
    }

    // Get an object from the handle map
    public T get(long handle) {
        T obj = map.get(handle);
        if (obj == null) {
            // TODO(murph): kotlin doesn't have checked exceptions, using runtime to repro. Not sure if we want Java
            //              to use checked itself or not
            throw new RuntimeException(new InternalException("UniffiHandleMap.get: Invalid handle"));
        }
        return obj;
    }

    // Remove an entry from the handlemap and get the Java object back
    public T remove(long handle) {
        T obj = map.remove(handle);
        if (obj == null) {
            // TODO(murph): kotlin doesn't have checked exceptions, using runtime to repro. Not sure if we want Java
            //              to use checked itself or not
            throw new RuntimeException(new InternalException("UniffiHandleMap: Invalid handle"));
        }
        return obj;
    }
}
