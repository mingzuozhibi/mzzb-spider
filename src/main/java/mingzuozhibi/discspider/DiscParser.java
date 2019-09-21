package mingzuozhibi.discspider;

import mingzuozhibi.common.jms.JmsMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.common.model.Result.formatErrorCause;

public class DiscParser {

    private static Pattern patternOfRank = Pattern.compile(" - ([,\\d]+)位");
    private static Pattern patternOfDate = Pattern.compile("(?<year>\\d{4})/(?<month>\\d{1,2})/(?<dom>\\d{1,2})");
    private static Pattern patternOfDateOfPreOrder = Pattern.compile("(?<month>\\d{1,2})月 (?<dom>\\d{1,2}), (?<year>\\d{4})日にリリース");

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private JmsMessage jmsMessage;
    private String asin;
    private Disc disc;

    public DiscParser(JmsMessage jmsMessage) {
        this.jmsMessage = jmsMessage;
    }

    public Optional<Disc> parse(String asin, String content) {
        this.asin = asin;
        this.disc = new Disc();
        return parse(Jsoup.parseBodyFragment(content));
    }

    private Optional<Disc> parse(Document document) {

        /*
         * 解析商品编号，应该与传入的相同
         * 解析发售日期，套装商品解析不到
         */

        parseAsinAndDate(document);

        if (StringUtils.isEmpty(disc.getAsin())) {
            jmsMessage.warning("解析信息：[%s][未发现商品编号]", asin);
            return Optional.empty();
        }
        if (!Objects.equals(disc.getAsin(), asin)) {
            jmsMessage.warning("解析信息：[%s][商品编号不符合]", asin);
            return Optional.empty();
        }

        /*
         * 解析碟片排名，碟片排名可以为空
         * 解析碟片标题，碟片标题应该存在
         */

        parseRankAndTitle(document);

        if (StringUtils.isEmpty(disc.getTitle())) {
            jmsMessage.warning("解析信息：[%s][未发现碟片标题]", asin);
        }

        /*
         * 解析碟片库存，商品缺货没有价格
         * 解析碟片价格，有货时应该有价格
         */

        parseStockAndPrice(document);

        if (!disc.isOutOfStock() && Objects.isNull(disc.getPrice())) {
            jmsMessage.warning("解析信息：[%s][未发现碟片价格]", asin);
        }

        /*
         * 解析碟片类型，三种方法确保获取
         * 解析套装商品，解析套装发售日期
         */

        parseTypeAndBuyset(document);

        if (disc.isBuyset()) {
            parseDateOfBuyset(document);
        }
        if (Objects.isNull(disc.getDate())) {
            jmsMessage.warning("解析信息：[%s][未发现发售日期]", asin);
        }

        return Optional.of(disc);
    }

    /*
     * 解析商品编号和发售日期
     */

    private void parseAsinAndDate(Document document) {
        for (Element element : document.select("td.bucket>div.content li")) {
            String line = element.text();
            // check asin
            if (line.startsWith("ASIN: ")) {
                disc.setAsin(line.substring(6));
            }
            // check date
            if (line.startsWith("発売日") || line.startsWith("CD")) {
                Matcher matcher = patternOfDate.matcher(line);
                if (matcher.find()) {
                    setDate(matcher);
                }
            }
        }
    }

    /*
     * 解析碟片排名和碟片标题
     */

    private void parseRankAndTitle(Document document) {
        // parse rank
        Matcher matcher = patternOfRank.matcher(document.select("#SalesRank").text());
        if (matcher.find()) {
            disc.setRank(parseNumber(matcher.group(1)));
        }
        // parse title
        String fullTitle = document.select("#productTitle").text();
        disc.setTitle(fullTitle.substring(0, Math.min(fullTitle.length(), 500)));
    }

    /*
     * 解析碟片库存和碟片价格
     */

    private void parseStockAndPrice(Document document) {
        if (document.select("#outOfStock").size() > 0) {
            disc.setOutOfStock(true);
        }
        Elements buynew = document.select("#buyNewSection");
        if (!buynew.isEmpty()) {
            disc.setPrice(parseNumber(buynew.first().text()));
        }
    }

    /*
     * 解析碟片类型和套装商品
     */

    private void parseTypeAndBuyset(Document document) {
        Elements elements = document.select(".swatchElement.selected");
        if (!elements.isEmpty()) {
            parseTypeBySwatch(elements.first().text());
        }

        if (Objects.isNull(disc.getType())) {
            parseTypeByLine(document);
        }

        if (Objects.isNull(disc.getType())) {
            tryGuessType(document);
        }
    }

    private void parseTypeBySwatch(String swatch) {
        String type = swatch.split("\\s+")[0];
        switch (type) {
            case "3D":
            case "4K":
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
                break;
            default:
                jmsMessage.warning("解析信息：[%s][未知碟片类型=%s]", asin, type);
        }
    }

    private void parseTypeByLine(Document document) {
        for (Element element : document.select("#bylineInfo span:not(.a-color-secondary)")) {
            switch (element.text()) {
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
                    return;
            }
        }
        jmsMessage.info("解析信息：[%s][未发现碟片类型]", asin);
    }

    private void tryGuessType(Document document) {
        String category = document.select("select.nav-search-dropdown option[selected]").text();
        if (Objects.equals("DVD", category)) {
            String title = document.select("#productTitle").text();
            boolean isBD = title.contains("[Blu-ray]");
            boolean isDVD = title.contains("[DVD]");
            boolean hasBD = title.contains("Blu-ray");
            boolean hasDVD = title.contains("DVD");
            if (isBD && !isDVD) {
                disc.setType("Bluray");
                jmsMessage.info("解析信息：[%s][推测类型为BD]", asin);
                return;
            }
            if (isDVD && !isBD) {
                disc.setType("Dvd");
                jmsMessage.info("解析信息：[%s][推测类型为DVD]", asin);
                return;
            }
            if (hasBD && !hasDVD) {
                jmsMessage.info("解析信息：[%s][推测类型为BD]", asin);
                disc.setType("Bluray");
                return;
            }
            if (hasDVD && !hasBD) {
                jmsMessage.info("解析信息：[%s][推测类型为DVD]", asin);
                disc.setType("Dvd");
                return;
            }
            jmsMessage.warning("解析信息：[%s][推测类型为DVD或BD]", asin);
            disc.setType("Auto");
            return;
        }
        jmsMessage.warning("解析信息：[%s][推测类型为其他]", asin);
        disc.setType("Other");
    }

    /*
     * 解析套装商品发售日期
     */

    private void parseDateOfBuyset(Document document) {
        for (Element element : document.select(".bundle-components .bundle-comp-preorder")) {
            Matcher matcher = patternOfDateOfPreOrder.matcher(element.text());
            if (matcher.find()) {
                setDate(matcher);
                return;
            }
        }
    }

    /*
     * 一些辅助方法
     */

    private void setBuyset() {
        if (!disc.isBuyset()) {
            disc.setBuyset(true);
            jmsMessage.info("解析信息：[%s][检测到套装商品]", asin);
        }
    }

    private void setDate(Matcher matcher) {
        String date = LocalDate.of(
            Integer.parseInt(matcher.group("year")),
            Integer.parseInt(matcher.group("month")),
            Integer.parseInt(matcher.group("dom"))
        ).format(formatter);
        disc.setDate(date);
    }

    private Integer parseNumber(String input) {
        try {
            StringBuilder builder = new StringBuilder();
            input.chars()
                .filter(cp -> cp >= '0' && cp <= '9')
                .forEach(builder::appendCodePoint);
            return Integer.parseInt(builder.toString());
        } catch (RuntimeException e) {
            jmsMessage.danger("解析信息：[%s][parseNumber error：%s]", asin, formatErrorCause(e));
            return null;
        }
    }

}
