package mingzuozhibi.spider.action;

import mingzuozhibi.spider.domain.Text;
import mingzuozhibi.spider.domain.TextService;
import mingzuozhibi.spider.spider.DiscRankSpider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static mingzuozhibi.spider.spider.DiscRankSpider.DiscInfoParser;

@RestController
public class DiscRankController extends BaseController {

    @Autowired
    private DiscRankSpider discRankSpider;

    @Autowired
    private TextService textService;

    @GetMapping("/discInfos/{asin}")
    public String discInfosAsinGet(@PathVariable String asin) {
        LOGGER.info("请求更新discInfos, ASIN={}", asin);
        DiscInfoParser parser = discRankSpider.fetchDiscInfo(asin);
        if (asin.equals(parser.getAsin())) {
            JSONObject discInfo = buildDiscInfo(parser);
            LOGGER.info("成功更新discInfos, data={}", discInfo);
            return objectResult(discInfo).toString();
        } else {
            return errorMessage("ASIN校验未通过: " + parser).toString();
        }
    }

    private JSONObject buildDiscInfo(DiscInfoParser parser) {
        JSONObject discInfo = new JSONObject();
        discInfo.put("asin", parser.getAsin());
        discInfo.put("type", parser.getType());
        discInfo.put("date", parser.getDate());
        discInfo.put("rank", parser.getRank());
        discInfo.put("title", parser.getTitle());
        return discInfo;
    }

    @PutMapping("/discRanks/active")
    public String discRanksActivePut(@RequestBody List<String> asins) {
        LOGGER.info("请求更新discRanks, 共{}个", asins.size());
        textService.setText("activeAsins", String.join(",", asins));
        return objectResult(true).toString();
    }

    @GetMapping("/discRanks/active")
    public String discRanksActiveGet() {
        Text text = textService.getText("discRanks");
        if (text != null) {
            LOGGER.info("获取discRanks数据成功");
            return textResult(text, JSONArray::new).toString();
        } else {
            LOGGER.warn("获取discRanks数据失败");
            return errorMessage("未找到相关数据").toString();
        }
    }

    @PostMapping("/discRanks/active/fetch")
    @Scheduled(cron = "0 0 1/6 * * ?")
    public void startFetchDiscRanks() {
        new Thread(() -> {
            LOGGER.info("startFetchDiscRanks");
            Text text = textService.getText("activeAsins");
            if (text != null) {
                doFetchDiscRanks(text.getContent());
            }
        }).start();
    }

    @PostMapping("/discRanks/next/fetch")
    @Scheduled(cron = "0 0 3/6,4/6 * * ?")
    public void startFetchNextRanks() {
        new Thread(() -> {
            LOGGER.info("startFetchNextRanks");
            Text text = textService.getText("nextAsins");
            if (text != null) {
                doFetchDiscRanks(text.getContent());
            }
        }).start();
    }

    private void doFetchDiscRanks(String content) {
        if (content == null || content.isEmpty()) {
            LOGGER.info("doFetchDiscRanks break, content is empty.");
            return;
        }
        Set<String> asins = Arrays.stream(content.split(","))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        JSONArray discRanks = new JSONArray();
        Map<String, DiscInfoParser> discInfos = discRankSpider.fetchDiscInfos(asins);
        discInfos.forEach((asin, parser) -> discRanks.put(buildDiscInfo(parser)));
        textService.setText("discRanks", discRanks.toString());

        Set<String> thisAsins = discInfos.keySet();
        String nextAsins = asins.stream().filter(asin -> !thisAsins.contains(asin))
                .collect(Collectors.joining(","));
        textService.setText("nextAsins", nextAsins);
    }

}
