package com.mingzuozhibi.discinfo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.gson.GsonFactory;
import com.mingzuozhibi.commons.mylog.JmsMessage;
import com.mingzuozhibi.commons.mylog.JmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

import static com.mingzuozhibi.utils.FormatUtils.fmtDateTime;

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
            JsonArray root = new JsonArray();
            discs.forEach(json -> {
                root.add(gson.fromJson(json, JsonObject.class));
            });
            jmsService.sendJson("prev.update.discs", root.toString(), "size=" + discs.size());
            jmsMessage.success("正在同步上次更新结果：共%d个", root.size());
        }
    }

    @GetMapping("/sendLastUpdateDiscs")
    public void sendLastUpdateDiscs() {
        for (int index = 0; index < 100; index++) {
            String json = listOpts.index("history.update.discs", index);
            JsonObject object = gson.fromJson(json, JsonObject.class);
            LocalDateTime date = gson.fromJson(object.get("date"), LocalDateTime.class);
            JsonArray array = object.getAsJsonArray("updatedDiscs");
            if (array.size() == 0) continue;
            jmsService.sendJson("last.update.discs", json, "size=" + array.size());
            jmsMessage.success("正在同步[%s]更新结果：共%d个",
                date.format(fmtDateTime), array.size());
            break;
        }
    }

}
