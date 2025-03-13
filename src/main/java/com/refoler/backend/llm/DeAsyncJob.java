package com.refoler.backend.llm;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class DeAsyncJob<T> {
    AsyncRunnable<T> runnable;
    AtomicReference<T> resultAtomic;
    private final Object lock = new Object();

    public interface AsyncRunnable<T> {
        void run(DeAsyncJob<T> job);
    }

    public DeAsyncJob(AsyncRunnable<T> runnable) {
        this.runnable = runnable;
        this.resultAtomic = new AtomicReference<>(null);
    }

    @NotNull
    public T runAndWait() {
        runnable.run(this);
        synchronized (lock) {
            while (resultAtomic.get() == null) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.onSpinWait();
                }
            }
        }
        return resultAtomic.get();
    }

    public void setResult(@NotNull T resultObj) {
        synchronized (lock) {
            resultAtomic.set(resultObj);
            lock.notifyAll();
        }
    }
}