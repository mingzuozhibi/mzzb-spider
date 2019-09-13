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
    @Scheduled(cron = "0 0 1/6 * * ?")
    public void startFullUpdate() {
        jmsMessage.info("Start Full Update");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("Stop Full Update: Has Other Update");
        }
        List<String> asins = listOpts.range("need.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("Stop Full Update: No Asins");
        } else {
            runFetchDiscs(asins, true);
        }
        running.set(false);
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 0 3/6 * * ?")
    public void startNextUpdate() {
        jmsMessage.info("Start Next Update");
        if (!running.compareAndSet(false, true)) {
            jmsMessage.warning("Stop Next Update: Has Other Update");
        }
        List<String> asins = listOpts.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("Stop Next Update: No Asins");
        } else {
            runFetchDiscs(asins, false);
        }
        running.set(false);
    }

    private void runFetchDiscs(List<String> asins, boolean fullUpdate) {
        runWithDaemon(() -> {
            if (fullUpdate) {
                resetNextAsins(asins);
            }
            Map<String, DiscParser> discInfos = discSpider.fetchDiscs(asins);
            List<String> updatedDiscs = buildUpdatedDiscs(discInfos);
            updateDiscsWriter.writeUpdateDiscs(updatedDiscs, fullUpdate);
            updateDiscsSender.sendPrevUpdateDiscs();
            cleanNextAsins(discInfos.keySet());
            jmsMessage.info("Need Update Asins: Size = " + listOpts.size("need.update.asins"));
            jmsMessage.info("Done Update Discs: Size = " + listOpts.size("done.update.discs"));
            jmsMessage.info("Prev Update Discs: Size = " + listOpts.size("prev.update.discs"));
            jmsMessage.info("Next Update Asins: Size = " + listOpts.size("next.update.asins"));
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

    private void runWithDaemon(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            jmsMessage.warning(String.format("Thread %s: Exit: %s %s"
                , t.getName(), e.getClass().getName(), e.getMessage()));
        });
        thread.start();
    }

}
