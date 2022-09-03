package com.mingzuozhibi.history;

import java.util.ArrayList;
import java.util.List;

public record TaskOfHistory(String name, String url) {

    private static final String BASE_URL1 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    private static final String BASE_URL2 = "https://www.amazon.co.jp/s/query?i=dvd&" +
        "rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&" +
        "s=date-desc-rank&language=ja_JP&ref=sr_pg_1&page=";

    public static List<TaskOfHistory> buildTasks(int p1, int p2) {
        List<TaskOfHistory> tasks = new ArrayList<>(p1 + p2);
        for (var page = 1; page <= p1; page++) {
            tasks.add(new TaskOfHistory("深夜动画" + page, BASE_URL1 + page));
        }
        for (var page = 1; page <= p2; page++) {
            tasks.add(new TaskOfHistory("家庭动画" + page, BASE_URL2 + page));
        }
        return tasks;
    }

}
