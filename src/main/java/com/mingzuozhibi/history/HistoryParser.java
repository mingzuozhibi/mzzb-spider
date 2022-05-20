package com.mingzuozhibi.history;

import com.google.gson.JsonObject;
import com.mingzuozhibi.commons.base.BaseSupport;
import com.mingzuozhibi.support.JmsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mingzuozhibi.commons.amqp.AmqpEnums.HISTORY_UPDATE;
import static com.mingzuozhibi.support.JmsRecorder.writeContent;

@Slf4j
@Component
public class HistoryParser extends BaseSupport {

    private final Pattern pattern = Pattern.compile("/dp/([A-Z\\d]+)/");

    public void parse(JmsRecorder recorder, String content, String origin) {
        try {
            List<History> result = new LinkedList<>();
            int divCount = 0;

            for (String text : content.split(Pattern.quote("\n&&&\n"))) {
                if (text.contains(":search-result-")) {
                    String jsonText = text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1);
                    JsonObject json = gson.fromJson(jsonText, JsonObject.class);
                    String html = json.get("html").getAsString();
                    Document document = Jsoup.parseBodyFragment(html);
                    result.addAll(parseElement(document));
                    divCount++;
                }
            }

            if (divCount > 0) {
                recorder.jmsSuccessRow(origin, String.format("找到%d条数据", divCount));
                amqpSender.send(HISTORY_UPDATE, gson.toJson(result));
            } else {
                recorder.jmsFailedRow(origin, "页面数据不符合格式，或者登入已失效");
                writeContent(content, origin);
            }

        } catch (RuntimeException e) {
            recorder.jmsErrorRow(origin, e);
            writeContent(content, origin);
            log.warn("捕获异常", e);
        }
    }

    private List<History> parseElement(Element element) {
        List<History> results = new LinkedList<>();
        element.select(".a-size-base.a-link-normal.a-text-bold").forEach(e -> {
            Matcher matcher = pattern.matcher(e.attr("href"));
            if (matcher.find()) {
                History history = new History();
                history.setAsin(matcher.group(1));
                history.setType(e.text().trim());
                history.setTitle(getTitle(element, history));
                history.setCreateOn(Instant.now());
                results.add(history);
            }
        });
        return results;
    }

    private String getTitle(Element element, History history) {
        Element first = element.select(".a-color-base.a-text-normal").first();
        return first != null ? first.text().trim() : "[" + history.getAsin() + "]";
    }

}
