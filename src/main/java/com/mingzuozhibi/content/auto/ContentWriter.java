package com.mingzuozhibi.content.auto;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.content.Content;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

import static com.mingzuozhibi.commons.amqp.AmqpEnums.*;

@Component
public class ContentWriter extends BaseSupport {

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOps;

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public void resetNextUpdateAsins(List<String> asins) {
        listOps.trim(NEXT_UPDATE_ASINS, 1, 0);
        listOps.rightPushAll(NEXT_UPDATE_ASINS, asins);
    }

    public void resetAsinRankHash() {
        List<String> jsons = listOps.range(PREV_UPDATE_DISCS, 0, -1);
        if (jsons == null) return;
        jsons.forEach(json -> {
            Content discUpdate = gson.fromJson(json, Content.class);
            hashOps.put(RECORDS_ASIN_RANK, discUpdate.getAsin(), discUpdate.getRank());
        });
    }

    public void cleanPrevUpdateDiscs() {
        listOps.trim(PREV_UPDATE_DISCS, 1, 0);
    }

    public void cleanDoneUpdateDiscs() {
        listOps.trim(DONE_UPDATE_DISCS, 1, 0);
    }

    public void pushDoneUpdateDiscs(List<Content> contents) {
        contents.forEach(content -> {
            listOps.rightPush(DONE_UPDATE_DISCS, gson.toJson(content));
        });
    }

    public void pushPrevUpdateDiscs(List<Content> contents) {
        contents.forEach(content -> {
            listOps.rightPush(PREV_UPDATE_DISCS, gson.toJson(content));
        });
    }

    public void pushLastUpdateDiscs(List<Content> contents) {
        DateResult dateResult = new DateResult(contents);
        listOps.leftPush(LAST_UPDATE_DISCS, gson.toJson(dateResult));
        listOps.trim(LAST_UPDATE_DISCS, 0, 99);
    }

    public void cleanNextUpdateAsins(Set<String> asins) {
        asins.forEach(asin -> {
            listOps.remove(NEXT_UPDATE_ASINS, 0, asin);
        });
    }

}
