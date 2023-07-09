package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.utils.EnvLoader;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

@Slf4j
public abstract class SpiderJsoup {

    public static Result<String> waitResultJsoup(String url) {
        try {
            var connection = Jsoup.connect(url)
                .referrer("https://www.amazon.co.jp/ref=nav_logo")
                .userAgent(SpiderUtils.USER_AGENT)
                .maxBodySize(10 * 1024 * 1024)
                .ignoreContentType(true);
            if (EnvLoader.isDevMode()) {
                log.info("In development mode, using proxy");
                connection.proxy("127.0.0.1", 7890);
            }
            return Result.ofData(connection.execute().body());
        } catch (Exception e) {
            log.warn("waitResultJsoup(url={}): {}", url, e.toString());
            return Result.ofError(e.toString());
        }
    }

}
