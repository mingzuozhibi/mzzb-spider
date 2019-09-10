package mingzuozhibi.spider.spider;

import mingzuozhibi.spider.support.LoggerSupport;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mingzuozhibi.spider.support.ChromeHelper.doInSessionFactory;
import static mingzuozhibi.spider.support.ChromeHelper.waitRequest;

@Service
public class DiscRankSpider extends LoggerSupport {

    public Map<String, DiscInfoParser> fetchDiscInfos(Set<String> asins) {
        LOGGER.info("扫描日亚排名：开始");
        Map<String, DiscInfoParser> discInfos = new HashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        LOGGER.info("扫描日亚排名：共{}个任务", asins.size());

        AtomicInteger errorCount = new AtomicInteger();

        doInSessionFactory(factory -> {
            for (String asin : asins) {
                LOGGER.info("扫描日亚排名({}/{}): ASIN={}", count.incrementAndGet(), asins.size(), asin);
                try {
                    Document document = waitRequest(factory, "https://www.amazon.co.jp/dp/" + asin);
                    DiscInfoParser parser = new DiscInfoParser(document);
                    if (parser.getAsin() == null) {
                        if (errorCount.incrementAndGet() >= 8) {
                            break;
                        } else {
                            LOGGER.info("扫描日亚排名({}/{}): NullPage({}->{})",
                                    count.get(), asins.size(), asin, errorCount.get());
                        }
                    } else {
                        if (asin.equals(parser.getAsin())) {
                            LOGGER.info("扫描日亚排名({}/{}): ASIN={}, RANK={}",
                                    count.get(), asins.size(), asin, parser.parseRank(document));
                            discInfos.put(asin, parser);
                        } else {
                            LOGGER.warn("扫描日亚排名({}/{}): DiffPage({}->{})",
                                    count.get(), asins.size(), asin, parser.getAsin());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.info("扫描日亚排名遇到错误: ASIN={}, ERROR={}: {}"
                            , asin, e.getClass().getName(), e.getMessage());
                }
            }
        });

        LOGGER.info("扫描日亚排名：完成({}/{})", discInfos.size(), asins.size());
        return discInfos;
    }

    public DiscInfoParser fetchDiscInfo(String asin) {
        LOGGER.info("扫描日亚碟片：开始");
        AtomicReference<DiscInfoParser> parserRef = new AtomicReference<>();
        doInSessionFactory(factory -> {
            try {
                Document document = waitRequest(factory, "https://www.amazon.co.jp/dp/" + asin);
                parserRef.set(new DiscInfoParser(document));
            } catch (Exception e) {
                LOGGER.info("扫描日亚碟片遇到错误: ASIN={}, ERROR={}: {}"
                        , asin, e.getClass().getName(), e.getMessage());
            }
        });
        LOGGER.info("扫描日亚碟片：完成");
        return parserRef.get();
    }

    public static class DiscInfoParser {

        private String title;
        private String type;
        private String date;
        private String asin;
        private Integer rank;

        private DiscInfoParser(Document document) {
            this.title = parseTitle(document);
            this.type = parseType(document);
            this.date = parseDate(document);
            this.asin = parseAsin(document);
            this.rank = parseRank(document);
        }

        public String getTitle() {
            return title;
        }

        public String getType() {
            return type;
        }

        public String getDate() {
            return date;
        }

        public String getAsin() {
            return asin;
        }

        public Integer getRank() {
            return rank;
        }

        @Override
        public String toString() {
            return "DiscInfoParser{" +
                    "title='" + title + '\'' +
                    ", type='" + type + '\'' +
                    ", date='" + date + '\'' +
                    ", asin='" + asin + '\'' +
                    ", rank=" + rank +
                    '}';
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

        private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");

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

}
