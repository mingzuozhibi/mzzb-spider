package mingzuozhibi.discspider;

import io.webfolder.cdp.session.SessionFactory;
import mingzuozhibi.common.jms.JmsMessage;
import mingzuozhibi.common.model.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static mingzuozhibi.common.util.ChromeUtils.doInSessionFactory;
import static mingzuozhibi.common.util.ChromeUtils.waitResult;
import static mingzuozhibi.discspider.SpiderRecorder.writeContent;

@Service
public class DiscSpider {

    @Autowired
    private JmsMessage jmsMessage;

    public Map<String, DiscParser> updateDiscs(List<String> asins) {
        SpiderRecorder recorder = new SpiderRecorder("amazon disc data", asins.size(), jmsMessage);
        recorder.jmsStartUpdate();

        Map<String, DiscParser> discInfos = new HashMap<>();
        doInSessionFactory(factory -> {
            for (String asin : asins) {
                if (recorder.checkBreakCount(5)) break;
                updateRow(factory, recorder, discInfos, asin);
            }
        });

        recorder.jmsSummary();
        recorder.jmsEndUpdate();
        return discInfos;
    }

    private void updateRow(SessionFactory factory, SpiderRecorder recorder, Map<String, DiscParser> discInfos, String asin) {
        recorder.jmsStartUpdateRow(asin);

        Result<String> bodyResult = waitResult(factory, "https://www.amazon.co.jp/dp/" + asin);
        if (recorder.checkUnfinished(bodyResult, asin)) {
            return;
        }

        String content = bodyResult.getContent();
        try {
            DiscParser parser = new DiscParser(content);

            if (Objects.equals(parser.getAsin(), asin)) {
                recorder.jmsSuccessRow(String.format("[%s][rank=%d]", asin, parser.getRank()));
                discInfos.put(asin, parser);
            } else if (hasAmazonNoSpider(content)) {
                recorder.jmsFailedRow("There seems to be an anti-robot system");
            } else {
                recorder.jmsFailedRow("Data does not match the format: " + parser.toString());
            }
        } catch (Exception e) {
            recorder.jmsErrorRow(e);
            writeContent(content, asin);
        }
    }

    private boolean hasAmazonNoSpider(String content) {
        return content.contains("api-services-support@amazon.com");
    }

}
