package com.mingzuozhibi.history;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
public class HistoryTask {

    String name;
    String url;

    private static final String BASE_URL1 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    private static final String BASE_URL2 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    public static List<HistoryTask> buildTasks(int p1, int p2) {
        List<HistoryTask> tasks = new ArrayList<>(p1 + p2);
        for (int page = 1; page <= p1; page++) {
            tasks.add(new HistoryTask("深夜动画" + page, BASE_URL1 + page));
        }
        for (int page = 1; page <= p2; page++) {
            tasks.add(new HistoryTask("家庭动画" + page, BASE_URL2 + page));
        }
        return tasks;
    }

}
