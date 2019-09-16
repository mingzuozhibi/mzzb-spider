package mingzuozhibi.discspider;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.BaseController;
import mingzuozhibi.common.jms.JmsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static mingzuozhibi.common.util.ThreadUtils.runWithDaemon;

@Slf4j
@RestController
public class UpdateDiscsRunner extends BaseController {

    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscSpider discSpider;
    @Autowired
    private UpdateDiscsWriter updateDiscsWriter;
    @Autowired
    private UpdateDiscsSender updateDiscsSender;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    private Gson gson = new Gson();

    private AtomicBoolean running = new AtomicBoolean(false);

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 2 1/4 * * ?")
    public void startFullUpdate() {
        jmsMessage.notify("计划任务：开始全量更新");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("任务终止：已有其他更新");
        }
        List<String> asins = listOpts.range("need.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("任务终止：无可更新数据");
        } else {
            runFetchDiscs(asins, true);
        }
        running.set(false);
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 2 3/4 * * ?")
    public void startNextUpdate() {
        jmsMessage.info("计划任务：开始补充更新");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("任务终止：已有其他更新");
        }
        List<String> asins = listOpts.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("任务终止：无可更新数据");
        } else {
            runFetchDiscs(asins, false);
        }
        running.set(false);
    }

    private void runFetchDiscs(List<String> asins, boolean fullUpdate) {
        runWithDaemon(jmsMessage, "runFetchDiscs: fullUpdate=" + fullUpdate, () -> {
            if (fullUpdate) {
                resetNextAsins(asins);
            }
            Map<String, DiscParser> discInfos = discSpider.updateDiscs(asins);
            List<String> updatedDiscs = buildUpdatedDiscs(discInfos);
            updateDiscsWriter.writeUpdateDiscs(updatedDiscs, fullUpdate);
            updateDiscsSender.sendPrevUpdateDiscs();
            cleanNextAsins(discInfos.keySet());
        });
    }

    private List<String> buildUpdatedDiscs(Map<String, DiscParser> discInfos) {
        return discInfos.values().stream()
            .map(gson::toJson)
            .collect(Collectors.toList());
    }

    private void resetNextAsins(List<String> asins) {
        listOpts.trim("next.update.asins", 1, 0);
        listOpts.rightPushAll("next.update.asins", asins);
    }

    private void cleanNextAsins(Set<String> updatedAsins) {
        updatedAsins.forEach(asin -> {
            listOpts.remove("next.update.asins", 0, asin);
        });
    }

}
