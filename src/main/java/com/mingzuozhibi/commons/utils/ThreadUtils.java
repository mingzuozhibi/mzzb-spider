package com.mingzuozhibi.commons.utils;

import com.mingzuozhibi.commons.mylog.JmsLogger;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public abstract class ThreadUtils {

    public static void runWithDaemon(String name, JmsLogger bind, Callback callback) {
        Thread thread = new Thread(() -> {
            try {
                callback.call();
            } catch (Exception e) {
                log.warn("runWithDaemon(name=" + name + ")", e);
                bind.error("runWithDaemon(name=%s): %s", name, e);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public interface Callback {
        void call() throws Exception;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void waitSecond(Object lock, int seconds) {
        synchronized (lock) {
            long target = Instant.now().plusSeconds(seconds).toEpochMilli();
            while (true) {
                long timeout = target - Instant.now().toEpochMilli();
                if (timeout > 0) {
                    try {
                        lock.wait(timeout);
                        break;
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    break;
                }
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void notifyAll(Object lock) {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

}
