package mingzuozhibi.spider.domain;

import mingzuozhibi.spider.support.LoggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NewDiscService extends LoggerSupport {

    @Autowired
    private NewDiscRepository newDiscRepository;

    @Transactional
    public void tryCreateNewDisc(String asin, String title) {
        if (asin != null && asin.length() > 0) {
            NewDisc newDisc = newDiscRepository.getOneByAsin(asin);
            if (newDisc == null) {
                newDiscRepository.save(new NewDisc(asin, title));
                LOGGER.info("扫描新碟片：发现新碟片(asin={},title={})", asin, title);
            } else if (!newDisc.getTitle().equals(title)) {
                newDisc.setTitle(title);
                LOGGER.info("扫描新碟片：更新标题中(asin={},title={})", asin, title);
            }
        }
    }

}
