package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.base.BaseKeys.Name;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import static com.mingzuozhibi.support.SpiderCdp4j.doWithSession;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@Slf4j
@Component
@LoggerBind(Name.SPIDER_CONTENT)
public class ContentSpider extends BaseSupport {

    public List<Content> fetchAllContent(List<TaskOfContent> tasks) {
        var recorder = new JmsRecorder(bind, "日亚排名", tasks.size());
        recorder.jmsStartUpdate();

        var results = new LinkedList<Content>();
        doWithSession(supplier -> {
            for (var task : tasks) {
                var asin = task.getAsin();
                try {
                    if (recorder.checkBreakCount(5)) break;
                    recorder.jmsStartUpdateRow(asin);
                    var result = fetchContent(asin, () -> waitResult(supplier, asin));
                    if (result.isSuccess()) {
                        var content = result.getData();
                        results.add(content);
                        if (content.isLogoff()) {
                            recorder.jmsSuccessRow(asin, "碟片可能已下架");
                        } else {
                            recorder.jmsSuccessRow(asin, "%d => %d".formatted(
                                task.getRank(), content.getRank()));
                        }
                    } else {
                        recorder.jmsFailedRow(asin, result.getMessage());
                    }
                } catch (Exception e) {
                    recorder.jmsErrorRow(asin, e);
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return results;
    }

    public Result<Content> fetchContent(String asin, Supplier<Result<String>> supplier) {
        var bodyResult = supplier.get();
        if (bodyResult.hasError()) {
            return Result.ofError(bodyResult.getMessage());
        }
        var body = bodyResult.getData();
        if (body.contains("api-services-support@amazon.com")) {
            if (body.contains("何かお探しですか？")) {
                var content = new Content();
                content.setAsin(asin);
                content.setLogoff(true);
                return Result.ofData(content);
            } else {
                return Result.ofError("发现日亚反爬虫系统");
            }
        }
        return new ContentParser(bind).parse(asin, body);
    }

}
