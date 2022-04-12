package com.mingzuozhibi.discinfos;

import com.mingzuozhibi.commons.model.Result;
import com.mingzuozhibi.commons.mylog.JmsMessage;
import com.mingzuozhibi.spider.SpiderRecorder;
import io.webfolder.cdp.session.SessionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mingzuozhibi.spider.SpiderCdp4j.doInSessionFactory;
import static com.mingzuozhibi.spider.SpiderRecorder.writeContent;
import static com.mingzuozhibi.spider.SpiderUtils.waitResult;

@Slf4j
@Component
public class DiscSpider {

    @Autowired
    private JmsMessage jmsMessage;

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public Map<String, DiscUpdate> updateDiscs(List<String> asins) {
        SpiderRecorder recorder = new SpiderRecorder("日亚排名", asins.size(), jmsMessage);
        recorder.jmsStartUpdate();

        Map<String, DiscUpdate> discUpdates = new LinkedHashMap<>();
        doInSessionFactory(factory -> {
            for (String asin : asins) {
                if (recorder.checkBreakCount(5)) break;
                SearchTask<DiscUpdate> task = doUpdateDisc(factory, recorder, new SearchTask<>(asin));
                if (task.isSuccess()) {
                    discUpdates.put(asin, task.getData());
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return discUpdates;
    }

    public SearchTask<DiscUpdate> doUpdateDisc(SessionFactory factory,
                                               SpiderRecorder recorder,
                                               SearchTask<DiscUpdate> task) {
        // 开始查询
        String asin = task.getKey();
        recorder.jmsStartUpdateRow(asin);
        Result<String> result = waitResult(factory, asin);
        if (recorder.checkUnfinished(asin, result)) {
            return task.withError(result.formatError());
        }
        String content = result.getContent();

        // 发现反爬
        if (content.contains("api-services-support@amazon.com")) {
            if (content.contains("何かお探しですか？")) {
                DiscUpdate discUpdate = new DiscUpdate();
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
            Optional<DiscUpdate> discRef = new DiscParser(jmsMessage).parse(asin, content);

            // 数据异常
            if (!discRef.isPresent()) {
                writeContent(content, asin);
                recorder.jmsFailedRow(asin, "页面数据未通过校验");
                return task.withError("页面数据未通过校验");
            }

            // 解析成功
            DiscUpdate discUpdate = discRef.get();
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
