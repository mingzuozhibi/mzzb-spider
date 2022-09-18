package com.mingzuozhibi;

import com.mingzuozhibi.commons.base.BaseController;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.domain.SearchTask;
import com.mingzuozhibi.commons.logger.LoggerBind;
import com.mingzuozhibi.content.Content;
import com.mingzuozhibi.content.ContentSpider;
import com.mingzuozhibi.support.JmsRecorder;
import com.mingzuozhibi.support.SpiderCdp4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.atomic.AtomicReference;

import static com.mingzuozhibi.commons.base.BaseKeys.Name;
import static com.mingzuozhibi.support.SpiderUtils.waitResult;

@RestController
@LoggerBind(Name.DEFAULT)
public class TestController extends BaseController {

    @Autowired
    private ContentSpider contentSpider;

    @GetMapping("/test/{asin}")
    public String fetchContent(@PathVariable String asin) {
        var task = new SearchTask<Content>(asin);
        var result = new AtomicReference<Result<Content>>();
        SpiderCdp4j.doInSessionFactory(supplier -> {
            var recorder = new JmsRecorder(bind, "碟片信息", 1);
            var taskResult = contentSpider.fetchContent(recorder, task,
                () -> waitResult(supplier, task.getKey()));
            result.set(Result.ofTask(taskResult));
        });
        return baseResult(result.get());
    }

}
