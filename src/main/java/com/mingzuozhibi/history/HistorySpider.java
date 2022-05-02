package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.mylog.JmsBind;
import com.mingzuozhibi.commons.mylog.JmsEnums.Name;
import com.mingzuozhibi.commons.mylog.JmsLogger;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static com.mingzuozhibi.support.SpiderJsoup.waitResultJsoup;

@Slf4j
@Service
@JmsBind(Name.SPIDER_HISTORY)
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
            recorder.jmsStartUpdateRow(task.getName());
            Result<String> bodyResult = waitResultJsoup(task.getUrl(), cookie.getData());
            if (recorder.checkUnfinished(task.getName(), bodyResult)) {
                continue;
            }
            historyParser.parse(recorder, bodyResult.getData(), task.getName());
        }

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
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
