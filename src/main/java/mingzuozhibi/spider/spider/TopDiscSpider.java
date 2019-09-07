package mingzuozhibi.spider.spider;

import mingzuozhibi.spider.domain.Text;
import mingzuozhibi.spider.domain.TextRepository;
import mingzuozhibi.spider.domain.TextService;
import mingzuozhibi.spider.support.LoggerSupport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static mingzuozhibi.spider.support.ChromeHelper.*;

@Service
public class TopDiscSpider extends LoggerSupport {

    public static final String TOP_DISCS_DATA_KEY = "topDiscs";

    @Autowired
    private TextService textService;

    @Autowired
    private TextRepository textRepository;

    @Transactional
    public void reset() {
        LOGGER.info("重置排行榜：开始");
        String text = waitRequest("https://mingzuozhibi.com/proxy/topDiscs");
        JSONObject root = new JSONObject(text);
        Text topDiscs = textService.getText(TOP_DISCS_DATA_KEY);
        if (topDiscs == null) {
            topDiscs = new Text();
            topDiscs.setName(TOP_DISCS_DATA_KEY);
            textRepository.save(topDiscs);
        }
        topDiscs.setContent(root.getJSONArray("data").toString());
        topDiscs.setCreateOn(Instant.ofEpochMilli(root.getLong("createOn")));
        topDiscs.setUpdateOn(Instant.ofEpochMilli(root.getLong("updateOn")));
        LOGGER.info("重置排行榜：完成");
    }

    public void fetch() {
        LOGGER.info("扫描排行榜：开始");

        String baseUrl = "https://www.amazon.co.jp/gp/bestsellers/dvd";

        doInSessionFactory(factory -> {

            Set<String> animeAsins = new HashSet<>();

            for (int page = 1; page <= 5; page++) {
                String pageUrl = baseUrl + "/562020/ref=zg_bs_nav_d_1_d#" + page;
                Document document = waitRequest(factory, pageUrl);
                LOGGER.info("扫描排行榜：抓取深夜动画排行榜({}/5)", page);
                page = nextPage(page, document, element -> {
                    animeAsins.add(getAsin(element));
                });
                threadSleep(10);
            }

            for (int page = 1; page <= 5; page++) {
                String pageUrl = baseUrl + "/2201429051/ref=zg_bs_nav_d_2_562026#" + page;
                Document document = waitRequest(factory, pageUrl);
                LOGGER.info("扫描排行榜：抓取家庭动画排行榜({}/5)", page);
                page = nextPage(page, document, element -> {
                    animeAsins.add(getAsin(element));
                });
                threadSleep(10);
            }

            JSONArray topDiscs = new JSONArray();
            AtomicInteger count = new AtomicInteger(0);
            Map<String, Integer> prevRanks = prevRanks();

            for (int page = 1; page <= 5; page++) {
                String pageUrl = baseUrl + "/ref=sv_d_2#" + page;
                Document document = waitRequest(factory, pageUrl);
                LOGGER.info("扫描排行榜：抓取DVD排行榜({}/5)", page);

                page = nextPage(page, document, element -> {
                    String asin = getAsin(element);
                    String title = getTitle(element);
                    Integer thisRank = count.incrementAndGet();
                    Integer prevRank = prevRanks.get(asin);
                    boolean isAnime = animeAsins.contains(asin);

                    JSONObject disc = new JSONObject();
                    disc.put("id", thisRank);
                    disc.put("asin", asin);
                    disc.put("title", title);
                    disc.put("rank", thisRank);
                    disc.put("prev", prevRank);
                    disc.put("isAnime", isAnime);
                    topDiscs.put(disc);
                });

                threadSleep(10);
            }

            textService.setText(TOP_DISCS_DATA_KEY, topDiscs.toString());
            LOGGER.info("扫描排行榜：完成");
        });
    }

    private String getTitle(Element element) {
        return element.select(".p13n-sc-truncated").text();
    }

    private String getAsin(Element element) {
        Elements select = element.select(".p13n-asin");
        String attr = select.attr("data-p13n-asin-metadata");
        return new JSONObject(attr).getString("asin");
    }

    private int nextPage(int page, Document document, Consumer<Element> consumer) {
        Elements elements = document.select(".zg_itemRow");
        if (elements.size() != 20) {
            page--;
        } else {
            elements.forEach(consumer);
        }
        return page;
    }

    private Map<String, Integer> prevRanks() {
        Text topDiscs = textService.getText(TOP_DISCS_DATA_KEY);
        Map<String, Integer> prevRanks = new HashMap<>();
        if (topDiscs != null) {
            JSONArray prevDiscs = new JSONArray(topDiscs.getContent());
            for (int i = 0; i < prevDiscs.length(); i++) {
                JSONObject prevDisc = prevDiscs.getJSONObject(i);
                prevRanks.put(prevDisc.getString("asin"), prevDisc.getInt("rank"));
            }
        }
        return prevRanks;
    }

}
