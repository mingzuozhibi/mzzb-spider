package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class DiscController extends BaseController {

    @Autowired
    private JmsHelper jmsHelper;

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

    @GetMapping("/prevUpdateDiscs")
    public String prevUpdateDiscs() {
        List<String> discs = listOpts.range("prev.update.discs", 0, -1);
        if (discs == null) {
            jmsHelper.sendWarn("prevUpdateDiscs: no data");
            return errorMessage("prevUpdateDiscs: no data");
        } else {
            JsonArray data = new JsonArray();
            discs.forEach(json -> {
                data.add(gson.fromJson(json, JsonObject.class));
            });
            return objectResult(data);
        }
    }

    @GetMapping("/doneUpdateDiscs")
    public String doneUpdateDiscs() {
        List<String> discs = listOpts.range("done.update.discs", 0, -1);
        if (discs == null) {
            jmsHelper.sendWarn("doneUpdateDiscs: no data");
            return errorMessage("doneUpdateDiscs: no data");
        } else {
            JsonArray data = new JsonArray();
            discs.forEach(json -> {
                data.add(gson.fromJson(json, JsonObject.class));
            });
            return objectResult(data);
        }
    }

    @GetMapping("/startFullUpdate")
    @Scheduled(cron = "0 0 1/6 * * ?")
    public String startFullUpdate() {
        jmsHelper.sendInfo("start full update");
        List<String> asins = listOpts.range("need.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsHelper.sendWarn("stop full update: no asins");
            return errorMessage("stop full update: no asins");
        } else {
            listOpts.trim("next.update.asins", 1, 0);
            listOpts.rightPushAll("next.update.asins", asins);
            new Thread(() -> {
                writePrevUpdate(discSpider.fetchDiscs(asins), true);
            }).start();
            return objectResult(new JsonPrimitive("full update started"));
        }
    }

    @PostMapping("/startNextUpdate")
    @Scheduled(cron = "0 0 3/6 * * ?")
    public String startFetchNextRanks() {
        jmsHelper.sendInfo("start next update");
        List<String> asins = listOpts.range("next.update.asins", 0, -1);
        if (asins == null || asins.isEmpty()) {
            jmsHelper.sendWarn("stop next update: no asins");
            return errorMessage("stop next update: no asins");
        } else {
            new Thread(() -> {
                writePrevUpdate(discSpider.fetchDiscs(asins), false);
            }).start();
            return objectResult(new JsonPrimitive("next update started"));
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
        jmsHelper.sendInfo("剩余未抓取碟片数量：" + listOpts.size("next.update.asins"));

        sendPrevUpdateDiscs();
    }

    @GetMapping("/sendPrevUpdateDiscs")
    public void sendPrevUpdateDiscs() {
        List<String> discs = listOpts.range("prev.update.discs", 0, -1);
        if (discs == null) {
            jmsHelper.sendWarn("sendPrevUpdateDiscs: no data");
        } else {
            JsonArray discInfos = new JsonArray();
            discs.forEach(json -> {
                discInfos.add(gson.fromJson(json, JsonObject.class));
            });
            jmsHelper.doSend("prev.update.discs", discInfos.toString());
            log.info("JMS -> prev.update.discs size={}", discs.size());
        }
    }

}
