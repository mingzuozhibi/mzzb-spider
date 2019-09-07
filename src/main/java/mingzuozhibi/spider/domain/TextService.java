package mingzuozhibi.spider.domain;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class TextService {

    @Autowired
    private TextRepository textRepository;

    @Transactional
    public void setText(String name, String content) {
        Text topDiscs = textRepository.getOneByName(name);
        if (topDiscs != null) {
            topDiscs.setContent(content);
            topDiscs.setUpdateOn(Instant.now());
        } else {
            textRepository.save(new Text(name, content));
        }
    }

    @Transactional
    public Text getText(String name) {
        return textRepository.getOneByName(name);
    }

}
