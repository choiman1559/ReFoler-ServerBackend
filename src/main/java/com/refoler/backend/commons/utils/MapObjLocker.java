package com.refoler.backend.commons.utils;

import java.util.concurrent.atomic.AtomicReference;

public class MapObjLocker<T> {
    private final AtomicReference<T> lockObject;

    public MapObjLocker() {
        lockObject = new AtomicReference<>();
    }

    public MapObjLocker<T> setLockedObject(T lockObject) {
        this.lockObject.set(lockObject);
        return this;
    }

    public T getLockedObject() {
        return lockObject.get();
    }
}