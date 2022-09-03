package com.mingzuozhibi;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.commons.utils.ThreadUtils;
import com.mingzuozhibi.content.*;
import com.mingzuozhibi.history.HistorySpider;
import com.mingzuozhibi.history.TaskOfHistory;
import com.mingzuozhibi.support.JmsRecorder;
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
            bind.notify("抓取请求已接收：正在开始");
            startFetchHistory(json);
        } else {
            bind.notify("抓取任务已接收：忽略任务");
        }
    }

    public void startFetchHistory(String json) {
        ThreadUtils.runWithDaemon(bind, "上架抓取", () -> {
            try {
                bind.notify("上架抓取：开始");
                var tasks = TaskOfHistory.buildTasks(60, 10);
                var results = historySpider.fetchAllHistory(tasks);
                amqpSender.send(HISTORY_FINISH, gson.toJson(results));
                amqpSender.send(FETCH_TASK_DONE1, gson.toJson(true));
                bind.notify("上架抓取：结束");
            } catch (Exception e) {
                amqpSender.send(FETCH_TASK_DONE1, gson.toJson(false));
                bind.error(e.toString());
                bind.notify("上架抓取：失败");
            } finally {
                startFetchContent(json);
            }
        });
    }

    public void startFetchContent(String json) {
        ThreadUtils.runWithDaemon(bind, "排名抓取", () -> {
            try {
                bind.notify("排名抓取：开始");
                var parameterized = getParameterized(List.class, TaskOfContent.class);
                List<TaskOfContent> tasks = gson.fromJson(json, parameterized.getType());
                var results = contentSpider.fetchAllContent(tasks);
                amqpSender.send(CONTENT_FINISH, gson.toJson(results));
                amqpSender.send(FETCH_TASK_DONE2, gson.toJson(results.size()));
                bind.notify("排名抓取：结束");
            } catch (Exception e) {
                amqpSender.send(FETCH_TASK_DONE2, gson.toJson(0));
                bind.error(e.toString());
                bind.notify("排名抓取：失败");
            } finally {
                isStart.set(false);
            }
        });
    }

    @RabbitListener(queues = CONTENT_SEARCH)
    public void contentSearch(String json) {
        var typeToken = getParameterized(SearchTask.class, Content.class);
        SearchTask<Content> task = gson.fromJson(json, typeToken.getType());
        var recorder = new JmsRecorder(bind, "碟片信息", 1);
        var bodyResult = waitResult(null, task.getKey());
        var result = contentSpider.fetchContent(recorder, task, bodyResult);
        amqpSender.send(CONTENT_RETURN, gson.toJson(result));
    }

}
