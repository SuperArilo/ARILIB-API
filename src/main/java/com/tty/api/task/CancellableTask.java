package com.tty.api.task;

public interface CancellableTask {

    void cancel();
    boolean isCancelled();

}
