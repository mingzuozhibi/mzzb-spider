package com.mingzuozhibi.support;

import com.mingzuozhibi.commons.domain.Result;
import io.webfolder.cdp.Launcher;
import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
public abstract class SpiderCdp4j {

    public static void doInSessionFactory(Consumer<SessionFactory> consumer) {
        var uuid = UUID.randomUUID().toString().substring(0, 8);
        List<String> command = new ArrayList<>();
        command.add("--headless");
        command.add("--target.name=" + uuid);
        var launcher = new Launcher();
        try (var factory = launcher.launch(command)) {
            consumer.accept(factory);
        } catch (RuntimeException e) {
            log.warn(e.getMessage(), e);
        }
        killChrome(uuid);
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

    public static Result<String> waitResultCdp4j(SessionFactory factory, String url) {
        try (var session = factory.create()) {
            session.clearCookies();
            session.setUserAgent(SpiderUtils.USER_AGENT);
            session.navigate(url);
            session.waitDocumentReady(18000);
            session.wait(2000);
            return Result.ofData(session.getOuterHtml("body"));
        } catch (Exception e) {
            return Result.ofError(e.toString());
        }
    }

}
