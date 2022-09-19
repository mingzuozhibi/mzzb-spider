package com.mingzuozhibi.history;

import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.base.BaseKeys.Name;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.logger.LoggerBind;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
@LoggerBind(Name.SPIDER_HISTORY)
public class HistoryParser extends BaseSupport {

    private final Pattern asinPattern = Pattern.compile("/dp/([A-Z0-9]{10})/");
    private final Pattern datePattern = Pattern.compile("発売予定日は(\\d+)年(\\d+)月(\\d+)日です。");

    public Result<List<History>> parse(String body) {
        try {
            List<History> result = new LinkedList<>();
            for (var text : body.split(Pattern.quote("\n&&&\n"))) {
                if (text.contains(":search-result-")) {
                    var jsonText = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1);
                    var json = gson.fromJson(jsonText, JsonObject.class);
                    var html = json.get("html").getAsString();
                    var document = Jsoup.parseBodyFragment(html);
                    result.addAll(parseElement(document));
                }
            }
            return Result.ofData(result);
        } catch (Exception e) {
            bind.warning("解析失败：%s".formatted(e.toString()));
            return Result.ofError(e.toString());
        }
    }

    private List<History> parseElement(Element element) {
        List<History> results = new LinkedList<>();
        element.select(".a-size-base.a-link-normal.a-text-bold").forEach(type -> {
            var matcher = asinPattern.matcher(type.attr("href"));
            if (matcher.find()) {
                var history = new History();
                history.setAsin(matcher.group(1));
                history.setType(type.text().trim());
                history.setDate(getDate(element));
                history.setTitle(getTitle(element, history));
                history.setCreateOn(Instant.now());
                results.add(history);
            }
        });
        return results;
    }

    private String getDate(Element element) {
        var first = element.select("span[aria-label] .a-color-secondary").first();
        if (first == null) return null;
        var matcher = datePattern.matcher(first.text());
        if (!matcher.find()) return null;
        return "%s/%s/%s".formatted(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private String getTitle(Element element, History history) {
        var first = element.select(".a-color-base.a-text-normal").first();
        return first != null ? first.text().trim() : "[" + history.getAsin() + "]";
    }

}
