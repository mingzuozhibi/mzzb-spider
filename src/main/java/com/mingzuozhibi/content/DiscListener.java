package com.mingzuozhibi.content;

import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import com.mingzuozhibi.commons.base.BaseController;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.mylog.JmsLogger;
import com.mingzuozhibi.spider.JmsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import static com.google.gson.reflect.TypeToken.getParameterized;
import static com.mingzuozhibi.commons.mylog.JmsEnums.*;

@RestController
public class DiscListener extends BaseController {

    private JmsLogger bind;

    @PostConstruct
    public void bind() {
        bind = jmsSender.bind(Name.SPIDER_CONTENT);
    }

    @Autowired
    private DiscSpider discSpider;

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOpts;

    @JmsListener(destination = CONTENT_SEARCH)
    public void contentSearch(String json) {
        TypeToken<?> typeToken = getParameterized(SearchTask.class, DiscContent.class);
        SearchTask<DiscContent> task = gson.fromJson(json, typeToken.getType());
        JmsRecorder recorder = new JmsRecorder(bind, "碟片信息", 1);
        jmsSender.send(CONTENT_RETURN, gson.toJson(
            discSpider.doUpdateDisc(null, recorder, task)
        ));
    }

    @JmsListener(destination = NEED_UPDATE_ASINS)
    public void needUpdateAsins(String json) {
        JsonArray asins = gson.fromJson(json, JsonArray.class);
        listOpts.trim(NEED_UPDATE_ASINS, 1, 0);
        asins.forEach(jsonElement -> {
            listOpts.rightPush(NEED_UPDATE_ASINS, jsonElement.getAsString());
        });
        bind.debug("JMS <- %s size=%d", NEED_UPDATE_ASINS, asins.size());
    }

}
