package mingzuozhibi.spider.spider;

import io.webfolder.cdp.session.SessionFactory;
import mingzuozhibi.spider.domain.NewDisc;
import mingzuozhibi.spider.domain.NewDiscRepository;
import mingzuozhibi.spider.domain.NewDiscService;
import mingzuozhibi.spider.domain.TextService;
import mingzuozhibi.spider.support.LoggerSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static mingzuozhibi.spider.support.ChromeHelper.*;

@Service
public class NewDiscSpider extends LoggerSupport {

    public static final String NEW_DISCS_INFO_KEY = "newDiscsInfo";

    @Autowired
    private NewDiscRepository newDiscRepository;

    @Autowired
    private NewDiscService newDiscService;

    @Autowired
    private TextService textService;

    public void fetchFromAmazon() {
        LOGGER.info("扫描新碟片：开始");

        doInSessionFactory(factory -> {
            fetchPage(factory, "深夜动画", 60, "https://www.amazon.co.jp/s?i=dvd&rh=n%3A561958%2Cn%3A562002%2Cn%3A562020&s=date-desc-rank&language=ja_JP&ref=sr_pg_1");
            fetchPage(factory, "日间动画", 10, "https://www.amazon.co.jp/s?i=dvd&rh=n%3A561958%2Cn%3A%21562002%2Cn%3A562026%2Cn%3A2201429051&s=date-desc-rank&language=ja_JP&ref=sr_pg_1");
        });

        LOGGER.info("扫描新碟片：完成");
    }

    private void fetchPage(SessionFactory factory, String taskName, int maxPage, String baseUrl) {
        LOGGER.info("扫描新碟片：{}，共{}个", taskName, maxPage);
        AtomicBoolean isBreak = new AtomicBoolean(false);
        AtomicInteger noError = new AtomicInteger(0);

        for (int page = 1; page <= maxPage && !isBreak.get(); page++) {
            textService.setText(NEW_DISCS_INFO_KEY, String.format("系统运行正常，正在抓取数据(%d/%d)", page, maxPage));
            LOGGER.info("扫描新碟片：抓取中({}/{})", page, maxPage);
            String pageUrl = baseUrl + "&page=" + page;

            Document document = waitRequest(factory, pageUrl);

            Elements newResult = document.select(".s-result-list.sg-row > div[data-asin]");
            Elements oldResult = document.select("#s-results-list-atf > li[data-result-rank]");

            if (newResult.size() > 0) {
                List<Element> results = new ArrayList<>(newResult);
                LOGGER.info("扫描新碟片：解析到新版数据(发现{}条数据)", results.size());

                results.forEach(element -> {
                    String asin = element.attr("data-asin");
                    String title = element.select(".a-size-medium.a-color-base.a-text-normal").first().text();
                    newDiscService.tryCreateNewDisc(asin, title);
                });
                noError.set(0);
            } else if (oldResult.size() > 0) {
                List<Element> results = oldResult.stream()
                        .filter(element -> element.select(".s-sponsored-info-icon").size() == 0)
                        .collect(Collectors.toList());
                LOGGER.info("扫描新碟片：解析到旧版数据(发现{}条数据)", results.size());

                results.forEach(element -> {
                    String asin = element.attr("data-asin");
                    String title = element.select("a.a-link-normal[title]").stream()
                            .map(e -> e.attr("title"))
                            .collect(Collectors.joining(" "));

                    newDiscService.tryCreateNewDisc(asin, title);
                });
            } else {
                String outerHtml = document.outerHtml();
                if (outerHtml.contains("api-services-support@amazon.com")) {
                    textService.setText(NEW_DISCS_INFO_KEY, String.format("发现反爬系统(%d/%d)", page, maxPage));
                    LOGGER.error("扫描新碟片：发现反爬虫系统");
                    isBreak.set(true);
                } else if (noError.incrementAndGet() > 10) {
                    textService.setText(NEW_DISCS_INFO_KEY, String.format("发现异常数据(%d/%d)", page, maxPage));
                    LOGGER.error("扫描新碟片：连续十次数据异常[document={}]", document.outerHtml());
                    isBreak.set(true);
                }
            }

            LOGGER.info("扫描新碟片：休眠中({}/{})", page, maxPage);
            threadSleep(30);
        }

        if (!isBreak.get()) {
            textService.setText(NEW_DISCS_INFO_KEY, "系统运行正常，数据抓取完成");
        }
    }

    public void resetFromServer() {
        LOGGER.info("重置新碟片：开始");

        LinkedList<NewDisc> newDiscs = new LinkedList<>();

        int maxPage = maxPageFromServer();

        for (int page = 0; page <= maxPage; page++) {
            LOGGER.info("重置新碟片：抓取中({}/{})", page + 1, maxPage + 1);
            String pageUrl = "https://mingzuozhibi.com/api/newdiscs?page=" + page;
            String body = waitRequest(pageUrl);

            JSONObject root = new JSONObject(body);
            JSONObject data = root.getJSONObject("data");

            JSONArray newdiscs = data.getJSONArray("newdiscs");
            for (int i = 0; i < newdiscs.length(); i++) {
                JSONObject object = newdiscs.getJSONObject(i);
                String asin = object.getString("asin");
                String title = object.getString("title");
                long createOn = object.getLong("createTime");
                newDiscs.add(new NewDisc(asin, title, Instant.ofEpochMilli(createOn)));
            }
        }

        long localCount = newDiscRepository.count();
        long serveCount = newDiscs.size();
        LOGGER.info("重置新碟片：抓取完成, 本地数据={}, 远程数据={}", localCount, serveCount);

        newDiscRepository.deleteAll();
        while (!newDiscs.isEmpty()) {
            newDiscRepository.save(newDiscs.removeLast());
        }
        LOGGER.info("重置新碟片：全部完成");
    }

    private int maxPageFromServer() {
        String body = waitRequest("https://mingzuozhibi.com/api/newdiscs");
        JSONObject root = new JSONObject(body);
        JSONObject data = root.getJSONObject("data");
        JSONObject pageInfo = data.getJSONObject("pageInfo");
        return pageInfo.getInt("maxPage");
    }

}
