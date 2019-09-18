package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.gson.GsonFactory;
import mingzuozhibi.common.jms.JmsMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    private Gson gson = GsonFactory.createGson();

    private AtomicBoolean running = new AtomicBoolean(false);

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 2 1/4 * * ?")
    public void startFullUpdate() {
        jmsMessage.notify("计划任务：开始全量更新");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("任务终止：已有其他更新");
        }
        List<String> asins = listOps.range("need.update.asins", 0, -1);
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
        jmsMessage.notify("计划任务：开始补充更新");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("任务终止：已有其他更新");
        }
        List<String> asins = listOps.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.notify("任务终止：无可更新数据");
        } else {
            runFetchDiscs(asins, false);
        }
        running.set(false);
    }

    private void runFetchDiscs(List<String> asins, boolean fullUpdate) {
        runWithDaemon(jmsMessage, "runFetchDiscs: fullUpdate=" + fullUpdate, () -> {
            if (fullUpdate) {
                resetNextUpdateAsins(asins);
                writeAsinRankHash();
            }
            Map<String, DiscParser> discInfos = discSpider.updateDiscs(asins);
            updateDiscsWriter.writeUpdateDiscs(discInfos, fullUpdate);
            updateDiscsSender.sendPrevUpdateDiscs();
            cleanNextUpdateAsins(discInfos.keySet());
        });
    }

    private void resetNextUpdateAsins(List<String> asins) {
        listOps.trim("next.update.asins", 1, 0);
        listOps.rightPushAll("next.update.asins", asins);
    }

    private void writeAsinRankHash() {
        List<String> discs = listOps.range("done.update.discs", 0, -1);
        Objects.requireNonNull(discs).forEach(json -> {
            JsonObject disc = gson.fromJson(json, JsonObject.class);
            String asin = disc.get("asin").getAsString();
            if (disc.has("rank")) {
                int rank = disc.get("rank").getAsInt();
                hashOps.put("asin.rank.hash", asin, rank);
            } else {
                hashOps.delete("asin.rank.hash", asin);
            }
        });
    }

    private void cleanNextUpdateAsins(Set<String> updatedAsins) {
        updatedAsins.forEach(asin -> {
            listOps.remove("next.update.asins", 0, asin);
        });
    }

}
