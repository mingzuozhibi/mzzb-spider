package com.mingzuozhibi.spider;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

public abstract class SpiderJsoup {

    public static Result<String> waitResultJsoup(String url) {
        Result<String> result = new Result<>();
        try {
            Connection.Response execute = Jsoup.connect(url)
                .userAgent(SpiderUtils.USER_AGENT)
                .referrer("https://www.google.com/")
                .ignoreContentType(true)
                .maxBodySize(10 * 1024 * 1024)
                .execute();
            result.setContent(execute.body());
        } catch (Exception e) {
            result.pushError(e);
        }
        return result;
    }

}
