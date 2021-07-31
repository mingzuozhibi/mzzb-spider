package mingzuozhibi.discspider;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import mingzuozhibi.common.spider.SpiderRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static mingzuozhibi.common.spider.SpiderJsoup.waitRequest;
import static mingzuozhibi.common.spider.SpiderRecorder.writeContent;

@Slf4j
@Component
public class DiscSpider {

    @Autowired
    private JmsMessage jmsMessage;

    private ThreadLocal<DiscParser> discParser = ThreadLocal.withInitial(() -> new DiscParser(jmsMessage));

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public Result<Disc> updateDisc(String asin) {
        SpiderRecorder recorder = new SpiderRecorder("碟片信息", 1, jmsMessage);
        Result<Disc> result = new Result<>();
        result.syncResult(doUpdateDisc(recorder, asin));
        return result;
    }

    public Map<String, Disc> updateDiscs(List<String> asins) {
        SpiderRecorder recorder = new SpiderRecorder("日亚排名", asins.size(), jmsMessage);
        recorder.jmsStartUpdate();

        Map<String, Disc> discInfos = new LinkedHashMap<>();
        for (String asin : asins) {
            threadSleep();
            if (recorder.checkBreakCount(5)) break;
            Result<Disc> result = doUpdateDisc(recorder, asin);
            if (!result.isUnfinished()) {
                discInfos.put(asin, result.getContent());
            }
        }

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return discInfos;
    }

    private void threadSleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private Result<Disc> doUpdateDisc(SpiderRecorder recorder, String asin) {
        // 记录开始
        recorder.jmsStartUpdateRow(asin);

        // 开始抓取
        Result<String> bodyResult = waitRequest("https://www.amazon.co.jp/dp/" + asin + "?language=ja_JP",
            connection -> connection.maxBodySize(10 * 1024 * 1024));

        // 抓取失败
        if (recorder.checkUnfinished(asin, bodyResult)) {
            return Result.ofErrorMessage(bodyResult.formatError());
        }

        // 抓取成功
        String content = bodyResult.getContent();

        // 发现反爬
        if (hasAmazonNoSpider(content)) {
            if (content.contains("何かお探しですか？")) {
                recorder.jmsSuccessRow(asin, "可能该碟片已下架");
                Disc disc = new Disc();
                disc.setAsin(asin);
                disc.setOffTheShelf(true);
                return Result.ofContent(disc);
            } else {
                recorder.jmsFailedRow(asin, "发现日亚反爬虫系统");
                return Result.ofErrorMessage("发现日亚反爬虫系统");
            }
        }

        try {
            // 解析数据
            Optional<Disc> discRef = discParser.get().parse(asin, content);

            // 数据异常
            if (!discRef.isPresent()) {
                writeContent(content, asin);
                recorder.jmsFailedRow(asin, "页面数据未通过校验");
                return Result.ofErrorMessage("页面数据未通过校验");
            }

            // 解析成功
            Disc disc = discRef.get();
            Integer prevRank = hashOps.get("asin.rank.hash", asin);
            Integer thisRank = disc.getRank();
            recorder.jmsSuccessRow(asin, prevRank + " => " + thisRank);
            return Result.ofContent(disc);

        } catch (Exception e) {
            // 捕获异常
            recorder.jmsErrorRow(asin, e);
            writeContent(content, asin);
            log.warn("parsing error", e);
            return Result.ofErrorCause(e);
        }
    }

    private boolean hasAmazonNoSpider(String content) {
        return content.contains("api-services-support@amazon.com");
    }

}
