package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.base.BaseKeys.Name;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.mingzuozhibi.support.SpiderCdp4j.doInSessionFactory;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@Slf4j
@Component
@LoggerBind(Name.SPIDER_CONTENT)
public class ContentSpider extends BaseSupport {

    private final HashMap<String, Integer> ranks = new HashMap<>();

    public List<Content> fetchAllContent(List<TaskOfContent> tasks) {
        var recorder = new JmsRecorder(bind, "日亚排名", tasks.size());
        recorder.jmsStartUpdate();

        var results = new LinkedList<Content>();
        doInSessionFactory(factory -> {
            tasks.forEach(task -> ranks.put(task.getAsin(), task.getRank()));
            for (var task : tasks) {
                var asin = task.getAsin();
                if (recorder.checkBreakCount(5)) break;
                var bodyResult = waitResult(factory, asin);
                var result = fetchContent(recorder, new SearchTask<>(asin), bodyResult);
                if (result.isSuccess()) {
                    results.add(result.getData());
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return results;
    }

    public SearchTask<Content> fetchContent(
        JmsRecorder recorder, SearchTask<Content> task, Result<String> bodyResult
    ) {
        var asin = task.getKey();
        try {
            // 开始查询
            recorder.jmsStartUpdateRow(asin);
            if (bodyResult.hasError()) {
                recorder.jmsFailedRow(asin, bodyResult.getMessage());
                return task.withError(bodyResult.getMessage());
            }
            var body = bodyResult.getData();
            if (body.contains("api-services-support@amazon.com")) {
                if (body.contains("何かお探しですか？")) {
                    // 碟片下架
                    var content = new Content();
                    content.setAsin(asin);
                    content.setOffTheShelf(true);
                    recorder.jmsSuccessRow(asin, "碟片可能已下架");
                    return task.withData(content);
                } else {
                    // 发现反爬
                    recorder.jmsFailedRow(asin, "发现日亚反爬虫系统");
                    return task.withError("发现日亚反爬虫系统");
                }
            }
            var result = new ContentParser(bind).parse(asin, body);
            // 数据异常
            if (result.hasError()) {
                recorder.jmsFailedRow(asin, "解析失败：[%s][%s]".formatted(asin, result.getMessage()));
                return task.withError(result.getMessage());
            }
            // 解析成功
            var content = result.getData();
            var prevRank = ranks.getOrDefault(asin, null);
            var thisRank = content.getRank();
            recorder.jmsSuccessRow(asin, prevRank + " => " + thisRank);
            return task.withData(content);

        } catch (Exception e) {
            // 捕获异常
            log.warn("fetchContent(%s)".formatted(asin), e);
            recorder.jmsErrorRow(asin, e);
            return task.withError(e.toString());
        }
    }

}
