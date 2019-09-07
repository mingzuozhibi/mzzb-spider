package mingzuozhibi.spider.action;

import mingzuozhibi.spider.domain.NewDisc;
import mingzuozhibi.spider.domain.NewDiscRepository;
import mingzuozhibi.spider.domain.Text;
import mingzuozhibi.spider.domain.TextService;
import mingzuozhibi.spider.spider.NewDiscSpider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static mingzuozhibi.spider.spider.NewDiscSpider.NEW_DISCS_INFO_KEY;

@RestController
public class NewDiscController extends BaseController {

    @Autowired
    private NewDiscRepository newDiscRepository;

    @Autowired
    private NewDiscSpider newDiscSpider;

    @Autowired
    private TextService textService;

    @GetMapping("/newDiscs")
    public String newDiscs(Pageable pageable) {
        Page<NewDisc> page = newDiscRepository.findAll(pageable);
        List<NewDisc> newDiscs = page.getContent();
        LOGGER.info("获取newDiscs数据成功, page={}", page.getNumber());

        JSONObject root = objectResult(buildData(newDiscs));
        root.put("page", buildPage(page));
        return root.toString();
    }

    @GetMapping("/newDiscsInfo")
    public String newDiscsInfo() {
        Text text = textService.getText(NEW_DISCS_INFO_KEY);
        if (text != null) {
            LOGGER.info("获取newDiscsInfo数据成功");
            return textResult(text, str -> str).toString();
        } else {
            LOGGER.warn("获取newDiscsInfo数据失败");
            return errorMessage("未找到相关数据").toString();
        }
    }

    @PostMapping("/newDiscs/fetch")
    public void newDiscsFetch() {
        LOGGER.info("请求更新newDiscs");
        startFetchNewDiscs();
    }

    @PostMapping("/newDiscs/reset")
    public void newDiscsReset() {
        LOGGER.info("请求重置newDiscs");
        startResetNewDiscs();
    }

    @Scheduled(cron = "0 0 5/6 * * ?")
    public void startFetchNewDiscs() {
        new Thread(() -> {
            newDiscSpider.fetchFromAmazon();
        }).start();
    }

    private void startResetNewDiscs() {
        new Thread(() -> {
            newDiscSpider.resetFromServer();
        }).start();
    }

    private JSONArray buildData(List<NewDisc> content) {
        JSONArray root = new JSONArray();
        content.forEach(newDisc -> {
            root.put(newDisc.toJSON());
        });
        return root;
    }

}
