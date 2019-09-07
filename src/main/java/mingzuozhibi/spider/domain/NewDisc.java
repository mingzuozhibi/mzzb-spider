package mingzuozhibi.spider.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONObject;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Instant;

@Entity
@Setter
@Getter
@NoArgsConstructor
public class NewDisc extends BaseModel {

    @Column(length = 20, nullable = false, unique = true)
    private String asin;

    @Column(length = 500, nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant createOn;


    public NewDisc(String asin, String title) {
        this.asin = asin;
        this.title = title;
        this.createOn = Instant.now();
    }

    public NewDisc(String asin, String title, Instant createOn) {
        this.asin = asin;
        this.title = title;
        this.createOn = createOn;
    }

    public JSONObject toJSON() {
        JSONObject object = new JSONObject();
        object.put("id", getId());
        object.put("asin", asin);
        object.put("title", title);
        object.put("createOn", createOn.toEpochMilli());
        return object;
    }

}
