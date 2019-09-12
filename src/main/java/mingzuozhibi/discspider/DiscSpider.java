package mingzuozhibi.discspider;

import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.atomic.AtomicReference;

import static mingzuozhibi.common.ChromeHelper.doInSessionFactory;
import static mingzuozhibi.common.ChromeHelper.waitRequest;


@Slf4j
@Service
public class DiscSpider {

    @Autowired
    private JmsHelper jmsHelper;

    public Map<String, DiscParser> fetchDiscInfos(List<String> asins) {
        jmsHelper.sendInfo("扫描日亚排名：准备开始");
        Map<String, DiscParser> discInfos = new HashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger();

        doInSessionFactory(factory -> {
            int taskCount = asins.size();
            jmsHelper.sendInfo(String.format("扫描日亚排名：共%d个任务", taskCount));

            for (String asin : asins) {
                count.incrementAndGet();
                jmsHelper.sendInfo(String.format("正在抓取(%d/%d)[%s]", count.get(), taskCount, asin));

                Document document = null;
                try {
                    document = waitRequest(factory, "https://www.amazon.co.jp/dp/" + asin);
                    DiscParser parser = new DiscParser(document);
                    if (Objects.equals(parser.getAsin(), asin)) {
                        discInfos.put(asin, parser);
                        errorCount.set(0);
                        jmsHelper.sendInfo(String.format("成功抓取(%d/%d)[%s][rank=%d]",
                                count.get(), taskCount, asin, parser.getRank()));
                    } else {
                        if (errorCount.get() >= 5) {
                            jmsHelper.sendWarn("扫描日亚排名：连续5次未能抓取排名，本次扫描终止");
                            break;
                        } else if (document.outerHtml().contains("api-services-support@amazon.com")) {
                            jmsHelper.sendWarn("扫描日亚排名：已发现日亚反爬虫系统，本次扫描终止");
                            break;
                        }
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    if (document != null) {
                        String outerHtml = document.outerHtml();
                        String path = writeToTempFile(outerHtml);
                        jmsHelper.sendWarn(String.format("抓取中发生了异常：%s %s [file=%s]",
                                e.getClass().getSimpleName(), e.getMessage(), path));
                    } else {
                        jmsHelper.sendWarn(String.format("未能成功抓取页面：%s %s",
                                e.getClass().getSimpleName(), e.getMessage()));
                    }
                    log.warn("抓取中发生了异常", e);
                    errorCount.incrementAndGet();
                }
            }
            jmsHelper.sendInfo(String.format("扫描日亚排名：本次扫描结束(%d/%d)", count.get(), taskCount));
        });
        return discInfos;
    }

    public DiscParser fetchDisc(String asin) throws Exception {
        log.info("开始抓取单个碟片[{}]", asin);
        AtomicReference<DiscParser> discRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        doInSessionFactory(factory -> {
            try {
                Document document = waitRequest(factory, "https://www.amazon.co.jp/dp/" + asin);
                DiscParser parser = new DiscParser(document);
                log.info("抓取单个碟片成功[{}][{}]", asin, parser);
                discRef.set(parser);
            } catch (RuntimeException e) {
                log.warn("抓取单个碟片失败[{}]", asin);
                errorRef.set(e);
            }
        });
        if (discRef.get() != null) {
            return discRef.get();
        } else {
            throw errorRef.get();
        }
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
