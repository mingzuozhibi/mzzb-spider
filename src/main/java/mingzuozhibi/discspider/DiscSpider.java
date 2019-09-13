package mingzuozhibi.discspider;

import lombok.extern.slf4j.Slf4j;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static mingzuozhibi.common.model.Result.formatErrors;
import static mingzuozhibi.common.util.ChromeUtils.doInSessionFactory;
import static mingzuozhibi.common.util.ChromeUtils.waitResult;

@Slf4j
@Service
public class DiscSpider {

    @Autowired
    private JmsMessage jmsMessage;

    public Result<DiscParser> fetchDisc(String asin) {
        Result<DiscParser> parserResult = new Result<>();
        doInSessionFactory(factory -> {
            // 抓取
            Result<String> bodyResult = waitResult(factory, "https://www.amazon.co.jp/dp/" + asin);
            if (bodyResult.notDone()) {
                parserResult.setErrorMessage("抓取中遇到错误：" + bodyResult.formatError());
                return;
            }
            // 解析
            try {
                Document document = Jsoup.parseBodyFragment(bodyResult.getContent());
                DiscParser parser = new DiscParser(document);
                if (Objects.equals(parser.getAsin(), asin)) {
                    parserResult.setContent(parser);
                } else if (hasAmazonNoSpider(document)) {
                    parserResult.setErrorMessage("已发现日亚反爬虫系统，可能是查询过于频繁");
                } else {
                    parserResult.setErrorMessage("ASIN校验未通过：" + parser);
                }
            } catch (Exception e) {
                parserResult.setErrorMessage("解析中发生错误：" + formatErrors(e));
                recordErrorContent(asin, bodyResult.getContent());
            }
        });
        return parserResult;
    }

    private boolean hasAmazonNoSpider(Document document) {
        return document != null && document.outerHtml().contains("api-services-support@amazon.com");
    }

    public Map<String, DiscParser> fetchDiscs(List<String> asins) {
        jmsMessage.notify("扫描日亚排名：准备开始");
        Map<String, DiscParser> discInfos = new HashMap<>();
        AtomicInteger fetchCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger();

        doInSessionFactory(factory -> {
            int taskCount = asins.size();
            jmsMessage.info(String.format("扫描日亚排名：共%d个任务", taskCount));

            for (String asin : asins) {

                if (checkBreak(fetchCount, errorCount)) break;

                // 抓取
                jmsMessage.info(String.format("正在抓取(%d/%d)[%s]", fetchCount.get(), taskCount, asin));
                Result<String> bodyResult = waitResult(factory, "https://www.amazon.co.jp/dp/" + asin);
                if (bodyResult.notDone()) {
                    jmsMessage.warning("抓取中遇到错误：" + bodyResult.formatError());
                    errorCount.incrementAndGet();
                    continue;
                }

                // 解析
                try {
                    Document document = Jsoup.parseBodyFragment(bodyResult.getContent());
                    DiscParser parser = new DiscParser(document);

                    if (Objects.equals(parser.getAsin(), asin)) {
                        jmsMessage.info(String.format("成功抓取(%d/%d)[%s][rank=%d]",
                            fetchCount.get(), taskCount, asin, parser.getRank()));
                        discInfos.put(asin, parser);
                        errorCount.set(0);
                    } else if (hasAmazonNoSpider(document)) {
                        jmsMessage.warning("扫描日亚排名：已发现日亚反爬虫系统");
                        errorCount.incrementAndGet();
                    } else {
                        jmsMessage.warning("扫描日亚排名：ASIN校验未通过：" + parser);
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    jmsMessage.warning("解析中发生错误：" + formatErrors(e));
                    recordErrorContent(asin, bodyResult.getContent());
                    errorCount.incrementAndGet();
                }

            }
            int doneCount = discInfos.size();
            jmsMessage.info(String.format("扫描日亚排名：本次共%d个任务，抓取了%d个，成功获得排名%d个",
                taskCount, fetchCount.get(), doneCount));
            jmsMessage.info(String.format("扫描日亚排名：抓取中失败%d个，%d个未抓取，下次还应抓取%d个",
                fetchCount.get() - doneCount, taskCount - fetchCount.get(), taskCount - doneCount));
        });
        jmsMessage.notify("扫描日亚排名：扫描结束");
        return discInfos;
    }

    private boolean checkBreak(AtomicInteger fetchCount, AtomicInteger errorCount) {
        if (errorCount.get() >= 5) {
            jmsMessage.warning("扫描日亚排名：连续5次发生错误");
            return true;
        } else {
            fetchCount.incrementAndGet();
        }
        return false;
    }

    private void recordErrorContent(String asin, String outerHtml) {
        String path = writeToTempFile(outerHtml);
        log.warn("解析中发生错误：Asin={}, file={}", asin, path);
    }

    private String writeToTempFile(String content) {
        try {
            File file = File.createTempFile("DiscShelfSpider", "html");
            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file))) {
                bufferedWriter.write(content);
                bufferedWriter.flush();
                return file.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "null";
    }

}
