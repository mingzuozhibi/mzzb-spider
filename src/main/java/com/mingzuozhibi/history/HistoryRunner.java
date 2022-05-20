package com.mingzuozhibi.history;

import com.mingzuozhibi.commons.amqp.AmqpEnums.Name;
import com.mingzuozhibi.commons.amqp.logger.LoggerBind;
import com.mingzuozhibi.commons.base.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.mingzuozhibi.commons.utils.ThreadUtils.runWithDaemon;
import static com.mingzuozhibi.history.HistoryTask.buildTasks;

@RestController
@LoggerBind(Name.SPIDER_HISTORY)
public class HistoryRunner extends BaseController {

    @Autowired
    private HistorySpider historySpider;

    @Scheduled(cron = "0 2 7/12 * * ?")
    @GetMapping("/startUpdate")
    public void startUpdate() {
        runWithDaemon(bind, "上架抓取", () -> {
            historySpider.runFetchTasks(buildTasks(60, 10));
        });
    }

    @GetMapping("/checkUpdate")
    public void checkUpdate() {
        runWithDaemon(bind, "测试抓取", () -> {
            historySpider.runFetchTasks(buildTasks(1, 1));
        });
    }

}
