package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

public abstract class SpiderJsoup {

    public static Result<String> waitResultJsoup(String url) {
        try {
            Connection.Response execute = Jsoup.connect(url)
//                .proxy("127.0.0.1", 7890)
                .userAgent(SpiderUtils.USER_AGENT)
                .referrer("https://www.google.com/")
                .ignoreContentType(true)
                .maxBodySize(10 * 1024 * 1024)
                .execute();
            return Result.ofData(execute.body());
        } catch (Exception e) {
            return Result.ofError(e.toString());
        }
    }

    public static Result<String> waitResultJsoup(String url, String cookie) {
        try {
            Connection.Response execute = Jsoup.connect(url)
//                .proxy("127.0.0.1", 7890)
                .header("cookie", cookie)
                .userAgent(SpiderUtils.USER_AGENT)
                .referrer("https://www.google.com/")
                .ignoreContentType(true)
                .maxBodySize(10 * 1024 * 1024)
                .execute();
            return Result.ofData(execute.body());
        } catch (Exception e) {
            return Result.ofError(e.toString());
        }
    }

}
