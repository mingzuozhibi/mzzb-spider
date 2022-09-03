package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
public abstract class SpiderJsoup {

    private static final boolean DEV_MODE = Objects.equals(System.getenv("DEV_MODE"), "TRUE");

    public static Result<String> waitResultJsoup(String url, Consumer<Connection> consumer) {
        try {
            var connection = Jsoup.connect(url)
                .referrer("https://www.google.com/")
                .userAgent(SpiderUtils.USER_AGENT)
                .maxBodySize(10 * 1024 * 1024)
                .ignoreContentType(true);
            log.info("DEV_MODE: {}", DEV_MODE);
            if (DEV_MODE) {
                connection.proxy("127.0.0.1", 7890);
            }
            if (consumer != null) {
                consumer.accept(connection);
            }
            return Result.ofData(connection.execute().body());
        } catch (Exception e) {
            log.warn("waitResultJsoup(url={}): {}", url, e);
            return Result.ofError(e.toString());
        }
    }

    public static Result<String> waitResultJsoup(String url) {
        return waitResultJsoup(url, null);
    }

}
