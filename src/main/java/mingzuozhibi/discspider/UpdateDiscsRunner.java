package mingzuozhibi.discspider;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static mingzuozhibi.common.util.ThreadUtils.runWithDaemon;

@Slf4j
@RestController
public class UpdateDiscsRunner {

    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscSpider discSpider;
    @Autowired
    private UpdateDiscsWriter updateDiscsWriter;
    @Autowired
    private UpdateDiscsSender updateDiscsSender;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOps;

    private AtomicBoolean running = new AtomicBoolean(false);

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 2 1/4 * * ?")
    public void startFullUpdate() {
        jmsMessage.notify("计划任务：开始全量更新");
        List<String> asins = listOps.range("need.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("任务终止：无可更新数据");
        } else {
            runWithDaemon(jmsMessage, "全量更新", () -> {
                if (!running.compareAndSet(false, true)) {
                    jmsMessage.warning("任务终止：已有其他更新");
                    return;
                }
                try {
                    runFetchDiscs(asins, true);
                } finally {
                    running.set(false);
                }
            });
        }
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 12 3/4 * * ?")
    public void startNextUpdate() {
        jmsMessage.notify("计划任务：开始补充更新");
        List<String> asins = listOps.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.notify("任务终止：无可更新数据");
        } else {
            runWithDaemon(jmsMessage, "补充更新", () -> {
                if (!running.compareAndSet(false, true)) {
                    jmsMessage.warning("任务终止：已有其他更新");
                    return;
                }
                try {
                    runFetchDiscs(asins, false);
                } finally {
                    running.set(false);
                }
            });
        }
    }

    private void runFetchDiscs(List<String> asins, boolean fullUpdate) {
        if (fullUpdate) {
            updateDiscsWriter.resetNextUpdateAsins(asins);
            updateDiscsWriter.resetAsinRankHash();
        }

        Map<String, Disc> resultMap = discSpider.updateDiscs(asins);

        if (fullUpdate) {
            updateDiscsWriter.cleanDoneUpdateDiscs();
            updateDiscsWriter.cleanPrevUpdateDiscs();
        } else {
            updateDiscsWriter.cleanPrevUpdateDiscs();
        }

        List<Disc> updatedDiscs = new ArrayList<>(resultMap.values());
        updateDiscsWriter.pushDoneUpdateDiscs(updatedDiscs);
        updateDiscsWriter.pushPrevUpdateDiscs(updatedDiscs);
        updateDiscsWriter.recordHistoryOfDate(updatedDiscs);
        updateDiscsWriter.cleanNextUpdateAsins(resultMap.keySet());
        updateDiscsSender.sendPrevUpdateDiscs();
    }

}
