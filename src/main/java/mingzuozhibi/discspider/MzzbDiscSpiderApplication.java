package mingzuozhibi.discspider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "mingzuozhibi")
public class MzzbDiscSpiderApplication {
    public static void main(String[] args) {
        SpringApplication.run(MzzbDiscSpiderApplication.class, args);
    }
}
