package mingzuozhibi.discspider;

import io.webfolder.cdp.session.SessionFactory;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import mingzuozhibi.common.spider.SpiderRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static mingzuozhibi.common.spider.SpiderCdp4j.doInSessionFactory;
import static mingzuozhibi.common.spider.SpiderCdp4j.waitResult;
import static mingzuozhibi.common.spider.SpiderRecorder.writeContent;

@Service
public class DiscSpider {

    @Autowired
    private JmsMessage jmsMessage;

    @Resource(name = "redisTemplate")
    private HashOperations<String, String, Integer> hashOps;

    public Result<Disc> updateDisc(String asin) {
        SpiderRecorder recorder = new SpiderRecorder("碟片信息", 1, jmsMessage);
        Result<Disc> result = new Result<>();
        doInSessionFactory(factory -> {
            result.syncResult(doUpdateDisc(factory, recorder, asin));
        });
        return result;
    }

    public Map<String, Disc> updateDiscs(List<String> asins) {
        SpiderRecorder recorder = new SpiderRecorder("日亚排名", asins.size(), jmsMessage);
        recorder.jmsStartUpdate();

        Map<String, Disc> discInfos = new LinkedHashMap<>();
        doInSessionFactory(factory -> {
            for (String asin : asins) {
                if (recorder.checkBreakCount(5)) break;
                Result<Disc> result = doUpdateDisc(factory, recorder, asin);
                if (!result.isUnfinished()) {
                    discInfos.put(asin, result.getContent());
                }
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return discInfos;
    }

    @SuppressWarnings("unchecked")
    private Result<Disc> doUpdateDisc(SessionFactory factory, SpiderRecorder recorder, String asin) {
        recorder.jmsStartUpdateRow(asin);

        Result<String> bodyResult = waitResult(factory, "https://www.amazon.co.jp/dp/" + asin);
        if (recorder.checkUnfinished(asin, bodyResult)) {
            return Result.ofErrorMessage(bodyResult.formatError());
        }

        String content = bodyResult.getContent();
        try {
            Disc disc = parseDisc(asin, content);

            if (Objects.equals(disc.getAsin(), asin)) {
                Integer prevRank = hashOps.get("asin.rank.hash", asin);
                Integer thisRank = disc.getRank();
                recorder.jmsSuccessRow(asin, prevRank + " => " + thisRank);
                return Result.ofContent(disc);

            } else if (hasAmazonNoSpider(content)) {
                recorder.jmsFailedRow(asin, "发现日亚反爬虫系统");
                return Result.ofErrorMessage("发现日亚反爬虫系统");

            } else {
                writeContent(content, asin);
                recorder.jmsFailedRow(asin, "页面数据未通过校验");
                return Result.ofErrorMessage("页面数据未通过校验");
            }
        } catch (Exception e) {
            recorder.jmsErrorRow(asin, e);
            writeContent(content, asin);
            return Result.ofExceptions(e);
        }
    }

    private Disc parseDisc(String asin, String content) {
        DiscParser parser = new DiscParser(content);
        parser.getMessages().forEach(message -> {
            jmsMessage.warning("解析信息：[%s][%s]", asin, message);
        });
        return parser.getDisc();
    }

    private boolean hasAmazonNoSpider(String content) {
        return content.contains("api-services-support@amazon.com");
    }

}
