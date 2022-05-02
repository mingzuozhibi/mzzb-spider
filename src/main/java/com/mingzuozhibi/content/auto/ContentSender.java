package com.mingzuozhibi.content.auto;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.mylog.JmsBind;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

import static com.mingzuozhibi.commons.mylog.JmsEnums.*;
import static com.mingzuozhibi.commons.utils.FormatUtils.fmtDateTime;

@RestController
@JmsBind(Name.SPIDER_CONTENT)
public class ContentSender extends BaseSupport {

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    @GetMapping("/sendDoneUpdateDiscs")
    public void sendDoneUpdateDiscs() {
        List<String> discs = listOpts.range(DONE_UPDATE_DISCS, 0, -1);
        if (discs == null || discs.size() == 0) {
            bind.warning("无法同步全量更新结果：没有数据");
        } else {
            jmsSender.send(DONE_UPDATE_DISCS, buildDiscs(discs));
            bind.success("正在同步全量更新结果：共%d个", discs.size());
        }
    }

    @GetMapping("/sendPrevUpdateDiscs")
    public void sendPrevUpdateDiscs() {
        List<String> discs = listOpts.range(PREV_UPDATE_DISCS, 0, -1);
        if (discs == null || discs.size() == 0) {
            bind.warning("无法同步上次更新结果：没有数据");
        } else {
            jmsSender.send(PREV_UPDATE_DISCS, buildDiscs(discs));
            bind.success("正在同步上次更新结果：共%d个", discs.size());
        }
    }

    private String buildDiscs(List<String> discs) {
        JsonArray array = new JsonArray();
        for (String json : discs) {
            array.add(gson.fromJson(json, JsonObject.class));
        }
        return array.toString();
    }

    @GetMapping("/sendLastUpdateDiscs")
    public void sendLastUpdateDiscs() {
        Long size = listOpts.size(LAST_UPDATE_DISCS);
        int count = size != null ? size.intValue() : 0;
        for (int index = 0; index < count; index++) {
            String json = listOpts.index(LAST_UPDATE_DISCS, index);
            DateResult result = gson.fromJson(json, DateResult.class);
            if (result.count() == 0) continue;
            jmsSender.send(LAST_UPDATE_DISCS, json);
            String format = "正在同步[%s]更新结果：共%d个";
            bind.success(format, fmtDateTime.format(result.getDate()), result.count());
            return;
        }
        bind.warning("不能同步最后更新结果：没有数据");
    }

}
