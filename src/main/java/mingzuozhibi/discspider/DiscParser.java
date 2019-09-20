package mingzuozhibi.discspider;

import mingzuozhibi.common.jms.JmsMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.common.model.Result.formatErrorCause;

public class DiscParser {

    private static Pattern patternOfRank = Pattern.compile(" - ([,\\d]+)位");
    private static Pattern patternOfDate = Pattern.compile("(?<year>\\d{4})/(?<month>\\d{1,2})/(?<dom>\\d{1,2})");
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JmsMessage jmsMessage;
    private Disc disc = new Disc();

    public DiscParser(String content, JmsMessage jmsMessage) {
        this.jmsMessage = jmsMessage;
        parse(Jsoup.parseBodyFragment(content));
    }

    public Disc getDisc() {
        return disc;
    }

    private void parse(Document document) {
        parseRank(document);
        parseTitle(document);
        parseAsinAndDate(document);
        parseTypeAndPrice(document);
    }

    private void parseRank(Document document) {
        Matcher matcher = patternOfRank.matcher(document.select("#SalesRank").text());
        if (matcher.find()) {
            disc.setRank(parseNumber(matcher.group(1)));
        }
    }

    private void parseTitle(Document document) {
        String title = document.select("#productTitle").text().trim();
        disc.setTitle(title.length() > 500 ? title.substring(0, 500) : title);
    }

    private void parseAsinAndDate(Document document) {
        for (Element element : document.select("td.bucket>div.content li")) {
            checkAsin(element);
            checkDate(element);
        }
        if (disc.getAsin() == null) {
            jmsMessage.warning("未发现Asin");
        }
        if (disc.getDate() == null) {
            jmsMessage.warning("未发现发售日期");
        }
    }

    private void checkAsin(Element element) {
        if (element.text().startsWith("ASIN: ")) {
            disc.setAsin(element.text().substring(6).trim());
        }
    }

    private void checkDate(Element element) {
        String line = element.text();
        if (!line.startsWith("発売日") && !line.startsWith("CD")) {
            return;
        }
        Matcher matcher = patternOfDate.matcher(line);
        if (matcher.find()) {
            String date = LocalDate.of(
                Integer.parseInt(matcher.group("year")),
                Integer.parseInt(matcher.group("month")),
                Integer.parseInt(matcher.group("dom"))
            ).format(formatter);
            disc.setDate(date);
        }
    }

    private void parseTypeAndPrice(Document document) {
        if (document.select("#outOfStock").size() > 0) {
            disc.setOutOfStock(true);
            jmsMessage.warning("目前缺货无价格");
        }

        Elements elements = document.select(".swatchElement.selected");
        if (elements.isEmpty()) {
            checkDateExtra(document);
            if (disc.getType() == null) {
                jmsMessage.warning("未发现碟片类型");
                tryGuessType(document);
            }
            return;
        }

        String[] split = elements.first().text().split("\\s+");
        String type = split[0].trim(), price = split[1].trim();

        if (!disc.isOutOfStock()) {
            disc.setPrice(parseNumber(price));
        }

        switch (type) {
            case "3D":
                disc.setTypeExtra("3D");
                // no break;
            case "4K":
                disc.setTypeExtra("4K");
                // no break;
            case "Blu-ray":
                disc.setType("Bluray");
                return;
            case "DVD":
                disc.setType("Dvd");
                return;
            case "CD":
                disc.setType("Cd");
                return;
            case "セット買い":
                setBuyset();
                // no break
            default:
                checkDateExtra(document);
        }

        if (disc.getType() == null) {
            if (!disc.isBuyset()) {
                jmsMessage.warning("未知碟片类型：" + type);
            }
            tryGuessType(document);
        }
    }

    private void checkDateExtra(Document document) {
        for (Element element : document.select("#bylineInfo span:not(.a-color-secondary)")) {
            String type = element.text().trim();
            switch (type) {
                case "Blu-ray":
                    disc.setType("Bluray");
                    return;
                case "DVD":
                    disc.setType("Dvd");
                    return;
                case "CD":
                    disc.setType("Cd");
                    return;
                case "セット買い":
                    setBuyset();
            }
        }
    }

    private void setBuyset() {
        if (!disc.isBuyset()) {
            disc.setBuyset(true);
            jmsMessage.warning("检测到套装商品");
        }
    }

    private void tryGuessType(Document document) {
        String group = document.select("select.nav-search-dropdown option[selected]").text();
        if (Objects.equals("DVD", group)) {
            String fullTitle = document.select("#productTitle").text().trim();
            boolean isBD = fullTitle.contains("[Blu-ray]");
            boolean isDVD = fullTitle.contains("[DVD]");
            boolean hasBD = fullTitle.contains("Blu-ray");
            boolean hasDVD = fullTitle.contains("DVD");
            if (isBD && !isDVD) {
                disc.setType("Bluray");
                jmsMessage.warning("推测类型为BD");
                return;
            }
            if (isDVD && !isBD) {
                disc.setType("Dvd");
                jmsMessage.warning("推测类型为DVD");
                return;
            }
            if (hasBD && !hasDVD) {
                jmsMessage.warning("推测类型为BD");
                disc.setType("Bluray");
                return;
            }
            if (hasDVD && !hasBD) {
                jmsMessage.warning("推测类型为DVD");
                disc.setType("Dvd");
                return;
            }
            jmsMessage.warning("推测类型为DVD大类");
            disc.setType("Auto");
            return;
        }
        jmsMessage.warning("推测类型为其他");
        disc.setType("Other");
    }

    private Integer parseNumber(String input) {
        try {
            StringBuilder builder = new StringBuilder();
            input.chars()
                .filter(cp -> cp >= '0' && cp <= '9')
                .forEach(builder::appendCodePoint);
            return Integer.parseInt(builder.toString());
        } catch (RuntimeException e) {
            jmsMessage.danger("parseNumber error: " + formatErrorCause(e));
            return null;
        }
    }

}
