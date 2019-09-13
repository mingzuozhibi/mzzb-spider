package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import mingzuozhibi.common.BaseController;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class DiscController extends BaseController {

    @Autowired
    private JmsMessage jmsMessage;
    @Autowired
    private DiscSpider discSpider;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    private Gson gson = new Gson();

    @GetMapping("/fetchDisc/{asin}")
    public String fetchDisc(@PathVariable String asin) {
        String prefix = "[" + asin + "]";
        jmsMessage.notify(prefix + "开始手动抓取碟片");
        Result<DiscParser> result = discSpider.fetchDisc(asin);
        if (result.notDone()) {
            String errorMessage = result.formatError();
            jmsMessage.warning(prefix + "手动抓取碟片失败：" + errorMessage);
            return errorMessage(errorMessage);
        }
        JsonElement discInfo = gson.toJsonTree(result.getContent());
        jmsMessage.success(prefix + "手动抓取碟片成功：" + discInfo.toString());
        return objectResult(discInfo);
    }

    @JmsListener(destination = "need.update.asins")
    public void needUpdateAsins(String json) {
        JsonArray asins = gson.fromJson(json, JsonArray.class);
        listOpts.trim("need.update.asins", 1, 0);
        asins.forEach(jsonElement -> {
            listOpts.rightPush("need.update.asins", jsonElement.getAsString());
        });
        jmsMessage.notify("JMS <- need.update.asins size=" + asins.size());
    }

}
