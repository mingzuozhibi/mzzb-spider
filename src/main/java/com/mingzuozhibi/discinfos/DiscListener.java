package com.mingzuozhibi.discinfos;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.mingzuozhibi.commons.base.BaseController;
import com.mingzuozhibi.commons.mylog.JmsMessage;
import com.mingzuozhibi.commons.mylog.JmsService;
import com.mingzuozhibi.spider.SpiderRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
public class DiscListener extends BaseController {

    @Autowired
    private Gson gson;

    @Autowired
    private JmsMessage jmsMessage;

    @Autowired
    private JmsService jmsService;

    @Autowired
    private DiscSpider discSpider;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    @JmsListener(destination = "send.disc.update")
    public void listenDiscUpdate(String json) {
        TypeToken<?> typeToken = TypeToken.getParameterized(SearchTask.class, DiscUpdate.class);
        SearchTask<DiscUpdate> task = gson.fromJson(json, typeToken.getType());
        SpiderRecorder recorder = new SpiderRecorder("碟片信息", 1, jmsMessage);
        jmsService.convertAndSend("back.disc.update", gson.toJson(
            discSpider.doUpdateDisc(null, recorder, task)
        ));
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
