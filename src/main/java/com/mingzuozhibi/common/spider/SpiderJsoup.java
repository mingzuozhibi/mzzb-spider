package com.mingzuozhibi.common.spider;

import com.mingzuozhibi.common.model.Result;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
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
            if (e instanceof HttpStatusException) {
                HttpStatusException he = (HttpStatusException) e;
                String newMessage = he.getMessage() + ", code=" + he.getStatusCode();
                result.pushError(new HttpStatusException(newMessage, he.getStatusCode(), he.getUrl()));
            } else {
                result.pushError(e);
            }
        }
        return result;
    }

}
