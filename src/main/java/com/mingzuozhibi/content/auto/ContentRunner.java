package com.mingzuozhibi.content.auto;

import com.mingzuozhibi.commons.amqp.logger.LoggerBind;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.content.Content;
import com.mingzuozhibi.content.ContentSpider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mingzuozhibi.commons.amqp.AmqpEnums.*;
import static com.mingzuozhibi.commons.utils.ThreadUtils.runWithDaemon;

@RestController
@LoggerBind(Name.SPIDER_CONTENT)
public class ContentRunner extends BaseSupport {

    @Autowired
    private ContentSpider contentSpider;

    @Autowired
    private ContentWriter contentWriter;

    @Autowired
    private ContentSender contentSender;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOps;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 12 1/4 * * ?")
    public void startFullUpdate() {
        bind.notify("计划任务：开始全量更新");
        List<String> asins = listOps.range(NEED_UPDATE_ASINS, 0, -1);
        if (asins == null || asins.isEmpty()) {
            bind.warning("任务终止：无可更新数据");
            return;
        }
        runWithDaemon(bind, "全量更新", () -> {
            runFetchDiscs(asins, true);
        });
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 12 3/4 * * ?")
    public void startNextUpdate() {
        bind.notify("计划任务：开始补充更新");
        List<String> asins = listOps.range(NEXT_UPDATE_ASINS, 0, -1);
        if (asins == null || asins.isEmpty()) {
            bind.notify("任务终止：无可更新数据");
            return;
        }
        runWithDaemon(bind, "补充更新", () -> {
            runFetchDiscs(asins, false);
        });
    }

    private void runFetchDiscs(List<String> asins, boolean fullUpdate) {
        if (!running.compareAndSet(false, true)) {
            bind.warning("任务终止：已有其他更新");
            return;
        }
        try {
            if (fullUpdate) {
                contentWriter.resetNextUpdateAsins(asins);
                contentWriter.resetAsinRankHash();
            }

            Map<String, Content> map = contentSpider.fetchContents(asins);

            if (fullUpdate) {
                contentWriter.cleanDoneUpdateDiscs();
                contentWriter.cleanPrevUpdateDiscs();
            } else {
                contentWriter.cleanPrevUpdateDiscs();
            }

            List<Content> contents = new ArrayList<>(map.values());
            contentWriter.pushDoneUpdateDiscs(contents);
            contentWriter.pushPrevUpdateDiscs(contents);
            contentWriter.pushLastUpdateDiscs(contents);
            contentWriter.cleanNextUpdateAsins(map.keySet());
            contentSender.sendPrevUpdateDiscs();
        } finally {
            running.set(false);
        }
    }

}
