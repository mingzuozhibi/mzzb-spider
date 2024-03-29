package com.mingzuozhibi.commons.base;

import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.domain.ResultPage;
import com.mingzuozhibi.commons.utils.LoggerUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Slf4j
public abstract class BaseController extends BaseSupport {

    protected static final String MEDIA_TYPE = MediaType.APPLICATION_JSON_VALUE;

    @ResponseBody
    @ExceptionHandler
    public String errorHandler(Exception e) throws Exception {
        LoggerUtils.logRequestIfExists();
        log.warn("errorHandler(e=%s)".formatted(e), e);
        return errorResult(e.toString());
    }

    protected <T> String baseResult(Result<T> base) {
        return gson.toJson(base);
    }

    protected <T> String dataResult(T data) {
        return baseResult(Result.ofData(data));
    }

    protected <T> String pageResult(List<T> data, ResultPage page) {
        return baseResult(Result.ofPage(data, page));
    }

}
