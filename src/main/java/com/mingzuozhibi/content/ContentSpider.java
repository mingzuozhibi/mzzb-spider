package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.mylog.JmsBind;
import com.mingzuozhibi.commons.mylog.JmsEnums.Name;
import com.mingzuozhibi.support.JmsRecorder;
import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

import static com.mingzuozhibi.support.JmsRecorder.writeContent;
import static com.mingzuozhibi.support.SpiderCdp4j.doInSessionFactory;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@Slf4j
@Component
@JmsBind(Name.SPIDER_CONTENT)
public class ContentSpider extends BaseSupport {

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public Map<String, Content> updateDiscs(List<String> asins) {
        JmsRecorder recorder = new JmsRecorder(bind, "日亚排名", asins.size());
        recorder.jmsStartUpdate();

        Map<String, Content> discUpdates = new LinkedHashMap<>();
        doInSessionFactory(factory -> {
            for (String asin : asins) {
                if (recorder.checkBreakCount(5)) break;
                SearchTask<Content> task = doUpdateDisc(factory, recorder, new SearchTask<>(asin));
                if (task.isSuccess()) {
                    discUpdates.put(asin, task.getData());
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return discUpdates;
    }

    public SearchTask<Content> doUpdateDisc(
        SessionFactory factory, JmsRecorder recorder, SearchTask<Content> task
    ) {
        // 开始查询
        String asin = task.getKey();
        recorder.jmsStartUpdateRow(asin);
        Result<String> result = waitResult(factory, asin);
        if (recorder.checkUnfinished(asin, result)) {
            return task.withError(result.getMessage());
        }
        String content = result.getData();

        // 发现反爬
        if (content.contains("api-services-support@amazon.com")) {
            if (content.contains("何かお探しですか？")) {
                Content discUpdate = new Content();
                discUpdate.setAsin(asin);
                discUpdate.setOffTheShelf(true);
                return task.withData(discUpdate);
            } else {
                writeContent(content, asin);
                recorder.jmsFailedRow(asin, "发现日亚反爬虫系统");
                return task.withError("发现日亚反爬虫系统");
            }
        }

        try {
            // 开始解析
            Optional<Content> discRef = new ContentParser(bind).parse(asin, content);

            // 数据异常
            if (!discRef.isPresent()) {
                writeContent(content, asin);
                recorder.jmsFailedRow(asin, "页面数据未通过校验");
                return task.withError("页面数据未通过校验");
            }

            // 解析成功
            Content discUpdate = discRef.get();
            Integer prevRank = hashOps.get("asin.rank.hash", asin);
            Integer thisRank = discUpdate.getRank();
            recorder.jmsSuccessRow(asin, prevRank + " => " + thisRank);
            return task.withData(discUpdate);

        } catch (Exception e) {
            // 捕获异常
            recorder.jmsErrorRow(asin, e);
            writeContent(content, asin);
            log.warn("parsing error", e);
            return task.withError(e.getMessage());
        }
    }

}
