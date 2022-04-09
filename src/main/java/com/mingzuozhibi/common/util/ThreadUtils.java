package com.mingzuozhibi.common.util;

import com.mingzuozhibi.common.jms.JmsMessage;
import com.mingzuozhibi.common.model.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ThreadUtils {

    public static void runWithDaemon(JmsMessage jmsMessage, String origin, Callback callback) {
        Thread thread = new Thread(() -> {
            try {
                callback.call();
            } catch (Throwable e) {
                jmsMessage.warning("runWithDaemon [%s] error: %s", origin, Result.formatErrorCause(e));
                log.warn("runWithDaemon " + origin + " error:", e);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public interface Callback {
        void call() throws Exception;
    }

}
