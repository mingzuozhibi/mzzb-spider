package mingzuozhibi.spider.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewDiscRepository extends JpaRepository<NewDisc, Long> {

    NewDisc getOneByAsin(String asin);

}
