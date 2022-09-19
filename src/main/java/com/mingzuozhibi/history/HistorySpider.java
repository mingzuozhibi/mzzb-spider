package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.base.BaseKeys.Name;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

import static com.mingzuozhibi.support.SpiderJsoup.waitResultJsoup;

@Slf4j
@Component
@LoggerBind(Name.SPIDER_HISTORY)
public class HistorySpider extends BaseSupport {

    public final Result<String> cookie = readCookie();

    @Autowired
    private HistoryParser historyParser;

    public List<History> fetchAllHistory(List<TaskOfHistory> tasks) {
        var recorder = new JmsRecorder(bind, "上架信息", tasks.size());
        recorder.jmsStartUpdate();

        var doneResults = new LinkedList<History>();
        for (var task : tasks) {
            var origin = task.getName();
            try {
                if (recorder.checkBreakCount(5)) break;
                recorder.jmsStartUpdateRow(origin);
                var result = fetchHistory(task);
                if (result.isSuccess()) {
                    var rows = result.getData().size();
                    if (rows > 0) {
                        doneResults.addAll(result.getData());
                        recorder.jmsSuccessRow(origin, "找到%d条数据".formatted(rows));
                    } else {
                        recorder.jmsFailedRow(origin, "页面数据不符合格式，或者登入已失效");
                    }
                } else {
                    recorder.jmsFailedRow(origin, result.getMessage());
                }
            } catch (Exception e) {
                recorder.jmsErrorRow(origin, e);
            }
        }

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return doneResults;
    }

    private Result<List<History>> fetchHistory(TaskOfHistory task) {
        if (cookie.isSuccess()) {
            var bodyResult = waitResultJsoup(task.getUrl(), connection -> {
                connection.header("cookie", cookie.getData());
            });
            if (bodyResult.isSuccess()) {
                return historyParser.parse(bodyResult.getData());
            } else {
                return Result.ofError(bodyResult.getMessage());
            }
        }
        return Result.ofError(cookie.getMessage());
    }

    private Result<String> readCookie() {
        try {
            var file = new File("etc", "amazon-cookie");
            if (file.exists() && file.isFile() && file.canRead()) {
                return Result.ofData(Files.readAllLines(file.toPath()).get(0));
            }
            log.error("读取Cookie失败：" + file.getAbsolutePath());
            return Result.ofError("读取Cookie失败");
        } catch (Exception e) {
            log.error("读取Cookie失败：", e);
            return Result.ofError("读取Cookie失败");
        }
    }

}
