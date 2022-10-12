package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import io.webfolder.cdp.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Random;
import java.util.function.Supplier;

import static com.mingzuozhibi.support.SpiderCdp4j.waitResultCdp4j;
import static com.mingzuozhibi.support.SpiderJsoup.waitResultJsoup;

@Slf4j
public abstract class SpiderUtils {

    public static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/105.0.0.0 Safari/537.36";

    public static Result<String> waitResult(Supplier<Session> supplier, String asin) {
        var url = buildUrl(asin);
        log.debug("{}: {}", asin, url);
        Result<String> result;
        if (supplier != null) {
            result = waitResultCdp4j(supplier, url);
        } else {
            result = waitResultJsoup(url);
        }
        if (result.test(StringUtils::isEmpty)) {
            return Result.ofError("抓取碟片[%s]：结果为空".formatted(asin));
        }
        return result;
    }

    public static String buildUrl(String asin) {
        return "https://www.amazon.co.jp/" + randomWord(35) + "/dp/" + asin + "/?" +
            randomWord(3) + "=" + randomWord(4) + "&" +
            "language=ja_JP&" +
            randomWord(5) + "=" + randomWord(6) + "&" +
            "_encoding=UTF8&" +
            randomWord(7) + "=" + randomWord(8);
    }

    private static String randomWord(int count) {
        var builder = new StringBuilder();
        for (var i = 0; i < count; i++) {
            builder.append(randomChar());
        }
        return builder.toString();
    }

    private static char randomChar() {
        return (char) (new Random().nextInt(26) + 'a');
    }

}
