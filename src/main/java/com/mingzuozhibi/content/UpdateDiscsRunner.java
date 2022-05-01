package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.mylog.JmsLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mingzuozhibi.commons.mylog.JmsEnums.*;
import static com.mingzuozhibi.commons.utils.ThreadUtils.runWithDaemon;

@Slf4j
@RestController
public class UpdateDiscsRunner extends BaseSupport {

    private JmsLogger bind;

    @PostConstruct
    public void bind() {
        bind = jmsSender.bind(Name.SPIDER_CONTENT);
    }

    @Autowired
    private DiscSpider discSpider;

    @Autowired
    private UpdateDiscsWriter updateDiscsWriter;

    @Autowired
    private UpdateDiscsSender updateDiscsSender;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOps;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 2 1/4 * * ?")
    public void startFullUpdate() {
        bind.notify("计划任务：开始全量更新");
        List<String> asins = listOps.range(NEED_UPDATE_ASINS, 0, -1);
        if (asins == null || asins.isEmpty()) {
            bind.warning("任务终止：无可更新数据");
            return;
        }
        runWithDaemon("全量更新", bind, () -> {
            runFetchDiscs(asins, true);
        });
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 2 3/4 * * ?")
    public void startNextUpdate() {
        bind.notify("计划任务：开始补充更新");
        List<String> asins = listOps.range(NEXT_UPDATE_ASINS, 0, -1);
        if (asins == null || asins.isEmpty()) {
            bind.notify("任务终止：无可更新数据");
            return;
        }
        runWithDaemon("补充更新", bind, () -> {
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
                updateDiscsWriter.resetNextUpdateAsins(asins);
                updateDiscsWriter.resetAsinRankHash();
            }

            Map<String, DiscContent> resultMap = discSpider.updateDiscs(asins);

            if (fullUpdate) {
                updateDiscsWriter.cleanDoneUpdateDiscs();
                updateDiscsWriter.cleanPrevUpdateDiscs();
            } else {
                updateDiscsWriter.cleanPrevUpdateDiscs();
            }

            List<DiscContent> updatedDiscs = new ArrayList<>(resultMap.values());
            updateDiscsWriter.pushDoneUpdateDiscs(updatedDiscs);
            updateDiscsWriter.pushPrevUpdateDiscs(updatedDiscs);
            updateDiscsWriter.pushLastUpdateDiscs(updatedDiscs);
            updateDiscsWriter.cleanNextUpdateAsins(resultMap.keySet());
            updateDiscsSender.sendPrevUpdateDiscs();
        } finally {
            running.set(false);
        }
    }

}
