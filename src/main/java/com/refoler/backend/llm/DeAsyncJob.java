package com.refoler.backend.llm;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class DeAsyncJob<T> {
    AsyncRunnable<T> runnable;
    AtomicReference<T> resultAtomic;

    public interface AsyncRunnable<T> {
        void run(DeAsyncJob<T> job);
    }

    public DeAsyncJob(AsyncRunnable<T> runnable) {
        this.runnable = runnable;
        this.resultAtomic = new AtomicReference<>(null);
    }

    @NotNull
    public synchronized T runAndWait() {
        runnable.run(this);
        while (resultAtomic.get() == null) {
            Thread.onSpinWait();
        }
        return resultAtomic.get();
    }

    public void setResult(@NotNull T resultObj) {
        resultAtomic.set(resultObj);
    }
}
