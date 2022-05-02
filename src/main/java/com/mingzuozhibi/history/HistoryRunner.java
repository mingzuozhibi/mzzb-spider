package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.base.BaseController;
import com.mingzuozhibi.commons.mylog.JmsEnums.Name;
import com.mingzuozhibi.commons.mylog.JmsLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

import static com.mingzuozhibi.commons.utils.ThreadUtils.runWithDaemon;
import static com.mingzuozhibi.history.HistoryTask.buildTasks;

@RestController
public class HistoryRunner extends BaseController {

    private JmsLogger bind;

    @PostConstruct
    public void bind() {
        bind = jmsSender.bind(Name.SPIDER_HISTORY);
    }

    @Autowired
    private HistorySpider historySpider;

    @Scheduled(cron = "0 2 7/12 * * ?")
    @GetMapping("/startUpdate")
    public void startUpdate() {
        runWithDaemon("startUpdate", bind, () -> {
            historySpider.runFetchTasks(buildTasks(60, 10));
        });
    }

    @GetMapping("/checkUpdate")
    public void checkUpdate() {
        runWithDaemon("testUpdate", bind, () -> {
            historySpider.runFetchTasks(buildTasks(1, 1));
        });
    }

}
