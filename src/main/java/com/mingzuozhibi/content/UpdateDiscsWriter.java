package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.base.BaseSupport;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

import static com.mingzuozhibi.commons.mylog.JmsEnums.*;

@Component
public class UpdateDiscsWriter extends BaseSupport {

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
            DiscContent discUpdate = gson.fromJson(json, DiscContent.class);
            hashOps.put(RECORDS_ASIN_RANK, discUpdate.getAsin(), discUpdate.getRank());
        });
    }

    public void cleanPrevUpdateDiscs() {
        listOps.trim(PREV_UPDATE_DISCS, 1, 0);
    }

    public void cleanDoneUpdateDiscs() {
        listOps.trim(DONE_UPDATE_DISCS, 1, 0);
    }

    public void pushDoneUpdateDiscs(List<DiscContent> updatedDiscs) {
        updatedDiscs.forEach(content -> {
            listOps.rightPush(DONE_UPDATE_DISCS, gson.toJson(content));
        });
    }

    public void pushPrevUpdateDiscs(List<DiscContent> updatedDiscs) {
        updatedDiscs.forEach(content -> {
            listOps.rightPush(PREV_UPDATE_DISCS, gson.toJson(content));
        });
    }

    public void pushLastUpdateDiscs(List<DiscContent> updatedDiscs) {
        DateResult dateResult = new DateResult(updatedDiscs);
        listOps.leftPush(LAST_UPDATE_DISCS, gson.toJson(dateResult));
        listOps.trim(LAST_UPDATE_DISCS, 0, 99);
    }

    public void cleanNextUpdateAsins(Set<String> updatedAsins) {
        updatedAsins.forEach(asin -> {
            listOps.remove(NEXT_UPDATE_ASINS, 0, asin);
        });
    }

}
