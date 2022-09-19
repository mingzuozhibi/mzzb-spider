package com.mingzuozhibi;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.commons.utils.ThreadUtils;
import com.mingzuozhibi.content.*;
import com.mingzuozhibi.history.HistorySpider;
import com.mingzuozhibi.history.TaskOfHistory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.gson.reflect.TypeToken.getParameterized;
import static com.mingzuozhibi.commons.base.BaseKeys.*;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@Component
@LoggerBind(Name.SERVER_CORE)
public class MzzbSpiderListener extends BaseSupport {

    private final AtomicBoolean isStart = new AtomicBoolean(false);

    @Autowired
    private HistorySpider historySpider;

    @Autowired
    private ContentSpider contentSpider;

    @RabbitListener(queues = FETCH_TASK_START)
    public void fetchTaskStart(String json) {
        if (isStart.compareAndSet(false, true)) {
            startFetchHistory(json);
        }
    }

    public void startFetchHistory(String json) {
        var logger = amqpSender.bind(Name.SPIDER_HISTORY);
        ThreadUtils.runWithDaemon(logger, "更新上架信息", () -> {
            try {
                var tasks = TaskOfHistory.buildTasks(35, 5);
                var results = historySpider.fetchAllHistory(tasks);
                amqpSender.send(HISTORY_FINISH, gson.toJson(results));
                amqpSender.send(FETCH_TASK_DONE1, gson.toJson(true));
            } catch (Exception e) {
                amqpSender.send(FETCH_TASK_DONE1, gson.toJson(false));
                logger.error(e.toString());
                logger.warning("更新上架信息：失败");
            } finally {
                startFetchContent(json);
            }
        });
    }

    public void startFetchContent(String json) {
        var logger = amqpSender.bind(Name.SPIDER_CONTENT);
        ThreadUtils.runWithDaemon(logger, "更新日亚排名", () -> {
            try {
                var parameterized = getParameterized(List.class, TaskOfContent.class);
                List<TaskOfContent> tasks = gson.fromJson(json, parameterized.getType());
                var results = contentSpider.fetchAllContent(tasks);
                amqpSender.send(CONTENT_FINISH, gson.toJson(results));
                amqpSender.send(FETCH_TASK_DONE2, gson.toJson(results.size()));
            } catch (Exception e) {
                amqpSender.send(FETCH_TASK_DONE2, gson.toJson(0));
                logger.error(e.toString());
                logger.warning("更新日亚排名：失败");
            }
        });
    }

    @RabbitListener(queues = CONTENT_SEARCH)
    public void contentSearch(String json) {
        var token = getParameterized(SearchTask.class, Content.class);
        SearchTask<Content> task = gson.fromJson(json, token.getType());
        var result = contentSpider.fetchContent(task.getKey(),
            () -> waitResult(null, task.getKey()));
        amqpSender.send(CONTENT_RETURN, gson.toJson(task.withResult(result)));
    }

}
