package com.mingzuozhibi.content;

import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.base.BaseSupport;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class UpdateDiscsWriter extends BaseSupport {

    @Resource(name = "redisTemplate")
    private ListOperations<String, String> listOps;
    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public void resetNextUpdateAsins(List<String> asins) {
        listOps.trim("next.update.asins", 1, 0);
        listOps.rightPushAll("next.update.asins", asins);
    }

    public void resetAsinRankHash() {
        List<String> discs = listOps.range("done.update.discs", 0, -1);
        hashOps.keys("asin.rank.hash").forEach(asin -> {
            hashOps.delete("asin.rank.hash", asin);
        });
        Objects.requireNonNull(discs).forEach(json -> {
            DiscContent discUpdate = gson.fromJson(json, DiscContent.class);
            hashOps.put("asin.rank.hash", discUpdate.getAsin(), discUpdate.getRank());
        });
    }

    public void cleanPrevUpdateDiscs() {
        listOps.trim("prev.update.discs", 1, 0);
    }

    public void cleanDoneUpdateDiscs() {
        listOps.trim("done.update.discs", 1, 0);
    }

    public void pushDoneUpdateDiscs(List<DiscContent> discUpdates) {
        discUpdates.forEach(discInfo -> {
            listOps.rightPush("done.update.discs", gson.toJson(discInfo));
        });
    }

    public void pushPrevUpdateDiscs(List<DiscContent> discUpdates) {
        discUpdates.forEach(discInfo -> {
            listOps.rightPush("prev.update.discs", gson.toJson(discInfo));
        });
    }

    public void recordHistoryOfDate(List<DiscContent> discUpdates) {
        JsonObject history = new JsonObject();
        history.addProperty("date", LocalDateTime.now().toString());
        history.add("updatedDiscs", gson.toJsonTree(discUpdates));
        listOps.leftPush("history.update.discs", history.toString());
        listOps.trim("history.update.discs", 0, 99);
    }

    public void cleanNextUpdateAsins(Set<String> updatedAsins) {
        updatedAsins.forEach(asin -> {
            listOps.remove("next.update.asins", 0, asin);
        });
    }

}
