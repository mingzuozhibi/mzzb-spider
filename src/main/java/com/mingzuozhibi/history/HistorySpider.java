package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.amqp.AmqpEnums.Name;
import com.mingzuozhibi.commons.amqp.logger.LoggerBind;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static com.mingzuozhibi.commons.amqp.AmqpEnums.HISTORY_FINISH;
import static com.mingzuozhibi.support.SpiderJsoup.waitResultJsoup;

@Slf4j
@Service
@LoggerBind(Name.SPIDER_HISTORY)
public class HistorySpider extends BaseSupport {

    @Autowired
    private HistoryParser historyParser;

    public void runFetchTasks(List<HistoryTask> tasks) {
        JmsRecorder recorder = new JmsRecorder(bind, "上架信息", tasks.size());
        recorder.jmsStartUpdate();

        Result<String> cookie = readCookie();
        if (cookie.hasError()) {
            bind.error(cookie.getMessage());
            return;
        }

        for (HistoryTask task : tasks) {
            if (recorder.checkBreakCount(5))
                break;
            recorder.jmsStartUpdateRow(task.name());
            Result<String> bodyResult = waitResultJsoup(task.url(), connection -> {
                connection.header("cookie", cookie.getData());
            });
            if (recorder.checkUnfinished(task.name(), bodyResult)) {
                continue;
            }
            historyParser.parse(recorder, bodyResult.getData(), task.name());
        }

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        amqpSender.send(HISTORY_FINISH, "done");
    }

    public Result<String> readCookie() {
        try {
            File file = new File("etc", "amazon-cookie");
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                return Result.ofError("未能读取到cookie: file can't read");
            }
            return Result.ofData(Files.readAllLines(file.toPath()).get(0));
        } catch (Exception e) {
            log.error("未能读取到cookie", e);
            return Result.ofError("未能读取到cookie: " + e);
        }
    }

}
