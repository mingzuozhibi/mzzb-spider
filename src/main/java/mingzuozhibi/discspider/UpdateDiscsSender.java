package mingzuozhibi.discspider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mingzuozhibi.common.gson.GsonFactory;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.jms.JmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
public class UpdateDiscsSender {

    @Autowired
    private JmsService jmsService;
    @Autowired
    private JmsMessage jmsMessage;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    private Gson gson = GsonFactory.createGson();

    @GetMapping("/sendPrevUpdateDiscs")
    public void sendPrevUpdateDiscs() {
        List<String> discs = listOpts.range("prev.update.discs", 0, -1);
        if (discs == null || discs.size() == 0) {
            jmsMessage.warning("无法同步上次更新结果：没有数据");
        } else {
            JsonArray discInfos = new JsonArray();
            discs.forEach(json -> {
                discInfos.add(gson.fromJson(json, JsonObject.class));
            });
            jmsService.sendJson("prev.update.discs", discInfos.toString(), "size=" + discs.size());
            jmsMessage.success("正在同步上次更新结果：共%d个", discs.size());
        }
    }

    @GetMapping("/sendDoneUpdateDiscs")
    public void sendDoneUpdateDiscs() {
        List<String> discs = listOpts.range("done.update.discs", 0, -1);
        if (discs == null || discs.size() == 0) {
            jmsMessage.warning("无法同步全量更新结果：没有数据");
        } else {
            JsonArray discInfos = new JsonArray();
            discs.forEach(json -> {
                discInfos.add(gson.fromJson(json, JsonObject.class));
            });
            jmsService.sendJson("done.update.discs", discInfos.toString(), "size=" + discs.size());
            jmsMessage.success("正在同步全量更新结果：共%d个", discs.size());
        }
    }

}
