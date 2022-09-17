package com.mingzuozhibi;

import com.mingzuozhibi.commons.base.BaseController;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.content.Content;
import com.mingzuozhibi.content.ContentSpider;
import com.mingzuozhibi.support.JmsRecorder;
import com.mingzuozhibi.support.SpiderCdp4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import static com.mingzuozhibi.commons.base.BaseKeys.*;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@RestController
@LoggerBind(Name.DEFAULT)
public class TestController extends BaseController {

    @Autowired
    private ContentSpider contentSpider;

    @GetMapping("/test/{asin}")
    public void fetchContent(@PathVariable String asin) {
        var task = new SearchTask<Content>(asin);
        SpiderCdp4j.doInSessionFactory(factory -> {
            var recorder = new JmsRecorder(bind, "碟片信息", 1);
            var result = contentSpider.fetchContent(recorder, task,
                () -> waitResult(factory, task.getKey()));
            amqpSender.send(CONTENT_RETURN, gson.toJson(result));
        });
    }

}
