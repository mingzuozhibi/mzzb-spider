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

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import static com.mingzuozhibi.commons.base.BaseKeys.*;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@RestController
@LoggerBind(Name.DEFAULT)
public class TestController extends BaseController {

    @Autowired
    private ContentSpider contentSpider;

    @GetMapping("/test/{asin}")
    public void contentSearch(@PathVariable String asin) {
        var task = new SearchTask<Content>(asin);
        SpiderCdp4j.doInSessionFactory(factory -> {
            var recorder = new JmsRecorder(bind, "碟片信息", 1);
            var bodyResult = waitResult(factory, task.getKey());
            if (bodyResult.isSuccess()) {
                try (var pw = new PrintWriter("var/output.html")) {
                    pw.println(bodyResult.getData());
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            var result = contentSpider.fetchContent(recorder, task, bodyResult);
            amqpSender.send(CONTENT_RETURN, gson.toJson(result));
        });
    }

}
