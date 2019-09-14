package mingzuozhibi.discspider;

import lombok.Getter;
import lombok.ToString;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@ToString
public class DiscParser {

    private String title;
    private String type;
    private String date;
    private String asin;
    private Integer rank;

    public DiscParser(String content) {
        Document document = Jsoup.parseBodyFragment(content);
        this.title = parseTitle(document);
        this.type = parseType(document);
        this.date = parseDate(document);
        this.asin = parseAsin(document);
        this.rank = parseRank(document);
    }

    private String parseTitle(Document document) {
        String title = document.select("#productTitle").text().trim();
        return title.length() > 500 ? title.substring(0, 500) : title;
    }

    private String parseType(Document document) {
        for (Element element : document.select("#bylineInfo span:not(.a-color-secondary)")) {
            String type = element.text();
            if (type.equals("Blu-ray")) {
                return "Bluray";
            }
            if (type.equals("DVD")) {
                return "Dvd";
            }
            if (type.equals("CD")) {
                return "Cd";
            }
        }

        String group = document.select("select.nav-search-dropdown option[selected]").text();
        if (group != null && group.trim().equals("DVD")) {
            String title = document.select("#productTitle").text().trim();

            boolean isBD = title.contains("[Blu-ray]");
            boolean isDVD = title.contains("[DVD]");
            boolean hasBD = title.contains("Blu-ray");
            boolean hasDVD = title.contains("DVD");

            if (isBD && !isDVD) {
                return "Bluray";
            }
            if (isDVD && !isBD) {
                return "Dvd";
            }
            if (hasBD && !hasDVD) {
                return "Bluray";
            }
            if (hasDVD && !hasBD) {
                return "Dvd";
            }
            return "Auto";
        }

        return "Other";
    }

    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String parseDate(Document document) {
        Pattern pattern = Pattern.compile("(?<year>\\d{4})/(?<month>\\d{1,2})/(?<dom>\\d{1,2})");
        for (Element element : document.select("td.bucket>div.content li")) {
            Matcher matcher = pattern.matcher(element.text());
            if (matcher.find()) {
                String date = LocalDate.of(
                    Integer.parseInt(matcher.group("year")),
                    Integer.parseInt(matcher.group("month")),
                    Integer.parseInt(matcher.group("dom"))
                ).format(formatter);
                if (type.equals("Cd") && element.text().contains("CD")) {
                    return date;
                }
                if (element.text().contains("発売日")) {
                    return date;
                }
            }
        }
        return null;
    }

    private String parseAsin(Document document) {
        for (Element element : document.select("td.bucket>div.content li")) {
            if (element.text().startsWith("ASIN: ")) {
                return element.text().substring(6).trim();
            }
        }
        return null;
    }

    private static Pattern rankReg = Pattern.compile(" - ([,\\d]+)位");

    private Integer parseRank(Document document) {
        Matcher matcher = rankReg.matcher(document.select("#SalesRank").text());
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1).replace(",", ""));
        }
        return null;
    }

}
