package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.BaseController;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.jms.JmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class DiscController extends BaseController {

    @Autowired
    private JmsService jmsService;
    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscSpider discSpider;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    private Gson gson = new Gson();

    @GetMapping("/fetchDisc/{asin}")
    public String fetchDisc(@PathVariable String asin) {
        try {
            DiscParser parser = discSpider.fetchDisc(asin);
            if (parser.getAsin() == null) {
                return errorMessage("可能是访问日亚过于频繁，请过段时间再试");
            }
            if (parser.getAsin().equals(asin)) {
                return objectResult(gson.toJsonTree(parser));
            } else {
                return errorMessage("ASIN校验未通过：" + gson.toJson(parser));
            }
        } catch (Exception e) {
            return errorMessage(String.format("抓取碟片失败：%s %s",
                    e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    @JmsListener(destination = "need.update.asins")
    public void needUpdateAsins(String json) {
        JsonArray asins = gson.fromJson(json, JsonArray.class);
        listOpts.trim("need.update.asins", 1, 0);
        asins.forEach(jsonElement -> {
            listOpts.rightPush("need.update.asins", jsonElement.getAsString());
        });
        log.info("JMS <- need.update.asins size={}", asins.size());
    }

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 0 1/6 * * ?")
    public void startFullUpdate() {
        jmsMessage.info("Start Full Update");
        List<String> asins = listOpts.range("need.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("Stop Full Update: No Asins");
        } else {
            listOpts.trim("next.update.asins", 1, 0);
            listOpts.rightPushAll("next.update.asins", asins);
            runWithDaemon(() -> {
                writePrevUpdate(discSpider.fetchDiscs(asins), true);
            });
        }
    }

    @GetMapping("/startNextUpdate")
    @Scheduled(cron = "0 0 3/6 * * ?")
    public void startNextUpdate() {
        jmsMessage.info("Start Next Update");
        List<String> asins = listOpts.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsMessage.warning("Stop Next Update: No Asins");
        } else {
            runWithDaemon(() -> {
                writePrevUpdate(discSpider.fetchDiscs(asins), false);
            });
        }
    }

    private void writePrevUpdate(Map<String, DiscParser> discInfos, boolean resetDone) {
        List<String> updatedDiscs = discInfos.values().stream()
                .map(gson::toJson)
                .collect(Collectors.toList());

        if (resetDone) {
            listOpts.trim("done.update.discs", 1, 0);
            listOpts.trim("prev.update.discs", 1, 0);
        } else {
            listOpts.trim("prev.update.discs", 1, 0);
        }

        listOpts.rightPushAll("done.update.discs", updatedDiscs);
        listOpts.rightPushAll("prev.update.discs", updatedDiscs);

        discInfos.keySet().forEach(asin -> {
            listOpts.remove("next.update.asins", 0, asin);
        });
        jmsMessage.info("Next Update Aains: Size = " + listOpts.size("next.update.asins"));

        sendPrevUpdateDiscs();
    }

    @GetMapping("/sendPrevUpdateDiscs")
    public void sendPrevUpdateDiscs() {
        List<String> discs = listOpts.range("prev.update.discs", 0, -1);
        if (discs == null) {
            jmsMessage.warning("sendPrevUpdateDiscs: no data");
        } else {
            JsonArray discInfos = new JsonArray();
            discs.forEach(json -> {
                discInfos.add(gson.fromJson(json, JsonObject.class));
            });
            jmsService.sendJson("prev.update.discs", discInfos.toString());
            log.info("JMS -> prev.update.discs size={}", discs.size());
        }
    }

    @GetMapping("/sendDoneUpdateDiscs")
    public void sendDoneUpdateDiscs() {
        List<String> discs = listOpts.range("done.update.discs", 0, -1);
        if (discs == null) {
            jmsMessage.warning("sendDoneUpdateDiscs: no data");
        } else {
            JsonArray discInfos = new JsonArray();
            discs.forEach(json -> {
                discInfos.add(gson.fromJson(json, JsonObject.class));
            });
            jmsService.sendJson("done.update.discs", discInfos.toString());
            log.info("JMS -> done.update.discs size={}", discs.size());
        }
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
