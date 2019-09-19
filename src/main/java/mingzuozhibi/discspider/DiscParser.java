package mingzuozhibi.discspider;

import jdk.nashorn.internal.ir.annotations.Ignore;
import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.common.model.Result.formatErrorCause;

@Getter
public class DiscParser {

    private static Pattern patternOfRank = Pattern.compile(" - ([,\\d]+)位");
    private static Pattern patternOfDate = Pattern.compile("(?<year>\\d{4})/(?<month>\\d{1,2})/(?<dom>\\d{1,2})");
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Disc disc = new Disc();

    @Ignore
    private List<String> messages = new LinkedList<>();

    public DiscParser(String content) {
        parse(Jsoup.parseBodyFragment(content));
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
        Elements elements = document.select(".swatchElement.selected");
        if (elements.isEmpty()) {
            tryGuessType(document);
            messages.add("Parsing empty, guessing as " + disc.getType());
            return;
        }
        String[] split = elements.first().text().split("\\s+");
        String type = split[0].trim(), price = split[1].trim();

        switch (type) {
            case "Blu-ray":
            case "3D":
            case "4K":
                disc.setType("Bluray");
                break;
            case "DVD":
                disc.setType("Dvd");
                break;
            case "CD":
                disc.setType("Cd");
                break;
            default:
                disc.setType("Other");
                tryGuessType(document);
                messages.add("Parsing other, guessing as " + disc.getType());
        }

        disc.setPrice(parseNumber(price));
    }

    private Integer parseNumber(String input) {
        try {
            StringBuilder builder = new StringBuilder();
            input.chars()
                .filter(cp -> cp >= '0' && cp <= '9')
                .forEach(builder::appendCodePoint);
            return Integer.parseInt(builder.toString());
        } catch (RuntimeException e) {
            messages.add("parseNumber error: " + formatErrorCause(e));
            return null;
        }
    }

    private void tryGuessType(Document document) {
        for (Element element : document.select("#bylineInfo span:not(.a-color-secondary)")) {
            String type = element.text();
            if (type.equals("Blu-ray")) {
                disc.setType("Bluray");
                return;
            }
            if (type.equals("DVD")) {
                disc.setType("Dvd");
                return;
            }
            if (type.equals("CD")) {
                disc.setType("Cd");
                return;
            }
        }
        String group = document.select("select.nav-search-dropdown option[selected]").text();
        if (Objects.equals("DVD", group)) {
            String fullTitle = document.select("#productTitle").text().trim();
            boolean isBD = fullTitle.contains("[Blu-ray]");
            boolean isDVD = fullTitle.contains("[DVD]");
            boolean hasBD = fullTitle.contains("Blu-ray");
            boolean hasDVD = fullTitle.contains("DVD");
            if (isBD && !isDVD) {
                disc.setType("Bluray");
            }
            if (isDVD && !isBD) {
                disc.setType("Dvd");
            }
            if (hasBD && !hasDVD) {
                disc.setType("Bluray");
            }
            if (hasDVD && !hasBD) {
                disc.setType("Dvd");
            }
            disc.setType("Auto");
        }
    }

}
