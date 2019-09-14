package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
        jmsMessage.notify("开始查询碟片信息[%s]", asin);
        Result<DiscParser> result = discSpider.updateDisc(asin);
        if (result.isUnfinished()) {
            return errorMessage("抓取失败：" + result.formatError());
        } else {
            return objectResult(gson.toJsonTree(result.getContent()));
        }
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
