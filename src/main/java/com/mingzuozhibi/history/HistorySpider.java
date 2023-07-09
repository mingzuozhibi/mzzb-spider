package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.base.BaseKeys.Name;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

import static com.mingzuozhibi.support.SpiderJsoup.waitResultJsoup;

@Slf4j
@Component
@LoggerBind(Name.SPIDER_HISTORY)
public class HistorySpider extends BaseSupport {

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
        var bodyResult = waitResultJsoup(task.getUrl());
        if (bodyResult.isSuccess()) {
            return historyParser.parse(bodyResult.getData());
        } else {
            return Result.ofError(bodyResult.getMessage());
        }
    }

}
