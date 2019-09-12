package mingzuozhibi.discspider.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TextRepository extends JpaRepository<Text, Long> {

    Text getOneByName(String name);

}
