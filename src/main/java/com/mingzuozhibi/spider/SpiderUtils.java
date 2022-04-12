package com.mingzuozhibi.spider;

import com.mingzuozhibi.commons.model.Result;
import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

import static com.mingzuozhibi.spider.SpiderCdp4j.waitResultCdp4j;

@Slf4j
public abstract class SpiderUtils {

    public static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/99.0.4844.74 Safari/537.36 " +
            "Edg/99.0.1150.46";

    public static Result<String> waitResult(SessionFactory factory, String asin) {
        String url = buildUrl(asin);
        log.debug("{}: {}", asin, url);
        Result<String> result;
        if (factory != null) {
            result = waitResultCdp4j(factory, url);
        } else {
            result = SpiderJsoup.waitResultJsoup(url);
        }
        return result;
    }

    public static String buildUrl(String asin) {
        return "https://www.amazon.co.jp/" + randomWord(25) + "/dp/" + asin + "/?" +
            randomWord(5) + "=" + randomWord(3) + "&" +
            "language=ja_JP&" +
            randomWord(6) + "=" + randomWord(4) + "&" +
            "_encoding=UTF8&" +
            randomWord(3) + "=" + randomWord(5);
    }

    private static String randomWord(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(randomChar());
        }
        return builder.toString();
    }

    private static char randomChar() {
        return (char) (new Random().nextInt(26) + 'a');
    }

}
