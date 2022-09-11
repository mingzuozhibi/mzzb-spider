package com.mingzuozhibi.content;

import com.mingzuozhibi.commons.domain.Result;
import com.mingzuozhibi.commons.logger.Logger;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mingzuozhibi.commons.utils.MyTimeUtils.fmtDate;

public class ContentParser {

    private static final Pattern patternOfRank =
        Pattern.compile("- ([\\d,]+)位");

    private static final Pattern patternOfDate =
        Pattern.compile("(?<year>\\d{4})/(?<month>\\d{1,2})/(?<dom>\\d{1,2})");

    private static final Pattern patternOfDate2 =
        Pattern.compile("(?<year>\\d{4})年(?<month>\\d{1,2})月(?<dom>\\d{1,2})日");

    private final Logger bind;

    public ContentParser(Logger bind) {
        this.bind = bind;
    }

    private String asin;
    private Content content;

    public Result<Content> parse(String asin, String content) {
        this.asin = asin;
        this.content = new Content();
        return parse(Jsoup.parseBodyFragment(content));
    }

    private Result<Content> parse(Document document) {

        /*
         * 解析商品编号，应该与传入的相同
         * 解析发售日期，套装商品解析不到
         */

        parseAsinAndDateAndRank(document);

        if (StringUtils.isEmpty(content.getAsin())) {
            return Result.ofError("未发现商品编号");
        }
        if (!Objects.equals(content.getAsin(), asin)) {
            return Result.ofError("商品编号不符合");
        }

        /*
         * 解析碟片排名，碟片排名可以为空
         * 解析碟片标题，碟片标题应该存在
         */

        parseTitle(document);

        if (StringUtils.isEmpty(content.getTitle())) {
            return Result.ofError("未发现碟片标题");
        }

        /*
         * 解析碟片库存，商品缺货没有价格
         * 解析碟片价格，有货时应该有价格
         */

        parseStockAndPrice(document);

        /*
         * 解析碟片类型，三种方法确保获取
         * 解析套装商品，解析套装发售日期
         */

        parseTypeAndBuyset(document);

        if (Objects.isNull(content.getDate())) {
            if (content.isBuyset()) {
                parseDateOfBuyset(document);
            } else {
                parseDateOfAvailability(document);
            }
        }
        if (Objects.isNull(content.getDate())) {
            if (content.isBuyset()) {
                bind.debug("解析信息：[%s][未发现套装发售日期]".formatted(asin));
            } else {
                bind.debug("解析信息：[%s][未发现发售日期]".formatted(asin));
            }
        }

        return Result.ofData(content);
    }

    /*
     * 解析商品编号和发售日期和碟片排名
     */

    private void parseAsinAndDateAndRank(Document document) {
        for (var element : document.select("#detailBulletsWrapper_feature_div span.a-list-item")) {
            var line = element.text();
            // check asin
            if (line.startsWith("ASIN")) {
                content.setAsin(line.substring(line.length() - 10));
            }
            // check date
            if (line.startsWith("発売日") || line.startsWith("CD") || line.startsWith("Blu-ray Audio")) {
                var matcher = patternOfDate.matcher(line);
                if (matcher.find()) {
                    setDate(matcher);
                }
            }
            // check rank
            if (line.startsWith("Amazon")) {
                var matcher = patternOfRank.matcher(line);
                if (matcher.find()) {
                    content.setRank(parseNumber(matcher.group(1)));
                }
            }
        }
    }

    /*
     * 解析碟片标题
     */

    private void parseTitle(Document document) {
        // parse title
        var fullTitle = document.select("#productTitle").text();
        content.setTitle(fullTitle.substring(0, Math.min(fullTitle.length(), 500)));
    }

    /*
     * 解析碟片库存和碟片价格
     */

    private void parseStockAndPrice(Document document) {
        if (document.select("#outOfStock").size() > 0) {
            content.setOutOfStock(true);
        }
        var buynew = document.select("#buyNewSection");
        if (!buynew.isEmpty()) {
            content.setPrice(parseNumber(getText(buynew)));
        }
    }

    private String getText(Elements buynew) {
        return Objects.requireNonNull(buynew.first()).text().trim();
    }

    /*
     * 解析碟片类型和套装商品
     */

    private void parseTypeAndBuyset(Document document) {
        parseTypeByLine(document);

        if (Objects.isNull(content.getType())) {
            parseTypeBySwatch(document);
        }

        if (Objects.isNull(content.getType())) {
            tryGuessType(document);
        }
    }

    private void parseTypeByLine(Document document) {
        for (var element : document.select("#bylineInfo span:not(.a-color-secondary)")) {
            //noinspection EnhancedSwitchMigration
            switch (element.text()) {
                case "Blu-ray":
                    content.setType("Bluray");
                    return;
                case "DVD":
                    content.setType("Dvd");
                    return;
                case "CD":
                    content.setType("Cd");
                    return;
                case "セット買い":
                    setBuyset();
                    return;
            }
        }
    }

    private void parseTypeBySwatch(Document document) {
        var elements = document.select("li.swatchElement.selected a>span");
        if (elements.size() > 0) {
            var type = getText(elements);
            switch (type) {
                case "3D", "4K", "Blu-ray" -> content.setType("Bluray");
                case "DVD" -> content.setType("Dvd");
                case "CD" -> content.setType("Cd");
                case "セット買い" -> setBuyset();
            }
        }
    }

    private void tryGuessType(Document document) {
        var category = document.select("select.nav-search-dropdown option[selected]").text();
        if (Objects.equals("ミュージック", category)) {
            content.setType("Cd");
            return;
        }
        if (Objects.equals("DVD", category)) {
            var title = document.select("#productTitle").text();
            var isBD = title.contains("[Blu-ray]");
            var isDVD = title.contains("[DVD]");
            var hasBD = title.contains("Blu-ray");
            var hasDVD = title.contains("DVD");
            var likeBD = title.contains("BD");
            if (isBD && !isDVD) {
                content.setType("Bluray");
                bind.debug("解析信息：[%s][推测类型为BD]".formatted(asin));
                return;
            }
            if (isDVD && !isBD) {
                content.setType("Dvd");
                bind.debug("解析信息：[%s][推测类型为DVD]".formatted(asin));
                return;
            }
            if (hasBD && !hasDVD) {
                bind.debug("解析信息：[%s][推测类型为BD]".formatted(asin));
                content.setType("Bluray");
                return;
            }
            if (hasDVD && !hasBD) {
                bind.debug("解析信息：[%s][推测类型为DVD]".formatted(asin));
                content.setType("Dvd");
                return;
            }
            if (likeBD) {
                bind.debug("解析信息：[%s][疑似类型为BD]".formatted(asin));
                content.setType("Bluray");
                return;
            }
            bind.debug("解析信息：[%s][推测类型为DVD或BD]".formatted(asin));
            content.setType("Auto");
        } else {
            bind.debug("解析信息：[%s][推测类型为其他]".formatted(asin));
            content.setType("Other");
        }
    }

    /*
     * 解析套装商品发售日期
     */

    private void parseDateOfBuyset(Document document) {
        var elements = document.select("#bundle-v2-btf-component-release-date-label-1");
        if (elements.size() == 1) {
            var matcher = patternOfDate2.matcher(getText(elements));
            if (matcher.find()) {
                setDate(matcher);
                bind.debug("解析信息：[%s][发现套装发售日期]".formatted(asin));
            }
        }
    }

    private void parseDateOfAvailability(Document document) {
        var elements = document.select("#availability>span.a-size-medium.a-color-success");
        if (elements.size() == 1) {
            var matcher = patternOfDate2.matcher(getText(elements));
            if (matcher.find()) {
                setDate(matcher);
                bind.debug("解析信息：[%s][发现疑似发售日期]".formatted(asin));
            }
        }
    }

    /*
     * 一些辅助方法
     */

    private void setBuyset() {
        if (!content.isBuyset()) {
            content.setBuyset(true);
            bind.debug("解析信息：[%s][检测到套装商品]".formatted(asin));
        }
    }

    private void setDate(Matcher matcher) {
        var date = LocalDate.of(
            Integer.parseInt(matcher.group("year")),
            Integer.parseInt(matcher.group("month")),
            Integer.parseInt(matcher.group("dom"))
        ).format(fmtDate);
        content.setDate(date);
    }

    private Integer parseNumber(String input) {
        try {
            var builder = new StringBuilder();
            input.chars()
                .filter(cp -> cp >= '0' && cp <= '9')
                .forEach(builder::appendCodePoint);
            return Integer.parseInt(builder.toString());
        } catch (RuntimeException e) {
            bind.debug("解析信息：[%s][parseNumber error：%s]".formatted(asin, e));
            return null;
        }
    }

}
