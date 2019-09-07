package mingzuozhibi.spider.action;

import mingzuozhibi.spider.domain.Text;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;

import java.util.function.Function;

public abstract class BaseController {

    protected final Logger LOGGER;

    public BaseController() {
        LOGGER = LoggerFactory.getLogger(getClass());
    }

    protected JSONObject objectResult(Object result) {
        JSONObject root = new JSONObject();
        root.put("success", true);
        root.put("data", result);
        return root;
    }

    protected JSONObject errorMessage(String message) {
        JSONObject root = new JSONObject();
        root.put("success", false);
        root.put("message", message);
        return root;
    }

    protected JSONObject buildPage(Page<?> page) {
        JSONObject root = new JSONObject();
        root.put("page", page.getNumber());
        root.put("size", page.getNumberOfElements());
        root.put("maxPage", page.getTotalPages() - 1);
        root.put("maxSize", page.getSize());
        return root;
    }

    protected JSONObject textResult(Text text, Function<String, Object> mapper) {
        JSONObject root = objectResult(mapper.apply(text.getContent()));
        root.put("createOn", text.getCreateOn().toEpochMilli());
        root.put("updateOn", text.getUpdateOn().toEpochMilli());
        return root;
    }

}
