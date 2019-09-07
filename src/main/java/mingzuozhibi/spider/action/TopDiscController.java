package mingzuozhibi.spider.action;

import mingzuozhibi.spider.domain.Text;
import mingzuozhibi.spider.domain.TextService;
import mingzuozhibi.spider.spider.TopDiscSpider;
import org.json.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static mingzuozhibi.spider.spider.TopDiscSpider.TOP_DISCS_DATA_KEY;

@RestController
public class TopDiscController extends BaseController {

    @Autowired
    private TopDiscSpider topDiscSpider;

    @Autowired
    private TextService textService;

    @GetMapping("/topDiscs")
    public String topDiscs() {
        Text text = textService.getText(TOP_DISCS_DATA_KEY);
        if (text != null) {
            LOGGER.info("获取topDiscs数据成功");
            return textResult(text, JSONArray::new).toString();
        } else {
            LOGGER.warn("获取topDiscs数据失败");
            return errorMessage("未找到相关数据").toString();
        }
    }

    //    @Scheduled(cron = "0 0 0/2 * * ?")
    @PostMapping("/topDiscs/fetch")
    public void topDiscsFetch() {
        LOGGER.info("请求更新topDiscs");
        new Thread(() -> {
            topDiscSpider.fetch();
        }).start();
    }

    @PostMapping("/topDiscs/reset")
    public void topDiscReset() {
        LOGGER.info("请求重置topDiscs");
        new Thread(() -> {
            topDiscSpider.reset();
        }).start();
    }

}
