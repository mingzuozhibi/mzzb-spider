package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import io.webfolder.cdp.Launcher;
import io.webfolder.cdp.session.Session;
import io.webfolder.cdp.session.SessionFactory;
import io.webfolder.cdp.type.network.Cookie;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
public abstract class SpiderCdp4j {

    public static void doInSessionFactory(Consumer<Supplier<Session>> consumer) {
        var uuid = UUID.randomUUID().toString().substring(0, 8);
        List<String> command = new ArrayList<>();
        command.add("--headless");
        command.add("--target.name=" + uuid);
        var launcher = new Launcher();
        try (var factory = launcher.launch(command)) {
            var browserContextId = createBrowserContext(factory);
            consumer.accept(() -> {
                return factory.create(browserContextId);
            });
        } catch (RuntimeException e) {
            log.warn(e.getMessage(), e);
        }
        killChrome(uuid);
    }

    private static String createBrowserContext(SessionFactory factory) {
        var browserContextId = factory.createBrowserContext();
        try (var session = factory.create(browserContextId)) {
            var network = session.getCommand().getNetwork();
            network.clearBrowserCookies();
            getCookies(factory).forEach(cookie -> {
                network.setCookie(cookie.getName(), cookie.getValue(), null, cookie.getDomain(),
                    cookie.getPath(), false, false, cookie.getSameSite(), cookie.getExpires());
            });
        }
        return browserContextId;
    }

    private static Stream<Cookie> getCookies(SessionFactory factory) {
        try (var session = factory.create()) {
            return session.getCommand().getNetwork()
                .getCookies(List.of(".amazon.co.jp")).stream()
                .filter(cookie -> cookie.getName().contains("acbjp"));
        }
    }

    private static void killChrome(String uuid) {
        var format = "ps -x | grep 'target.name=%s' | grep -v grep | awk '{print $1}' | xargs -t kill -9 2>&1";
        var cmds = new String[]{"/bin/bash", "-l", "-c", String.format(format, uuid)};
        try {
            var process = Runtime.getRuntime().exec(cmds);
            process.waitFor();
            log.info("kill chrome " + uuid);
        } catch (IOException | InterruptedException e) {
            log.info("kill chrome " + uuid + ": " + e.getMessage(), e);
        }
    }

    public static Result<String> waitResultCdp4j(Supplier<Session> supplier, String url) {
        try (var session = supplier.get()) {
            session.navigate(url);
            session.waitDocumentReady(18000);
            session.wait(2000);
            return Result.ofData(session.getOuterHtml("body"));
        } catch (Exception e) {
            return Result.ofError(e.toString());
        }
    }

}
