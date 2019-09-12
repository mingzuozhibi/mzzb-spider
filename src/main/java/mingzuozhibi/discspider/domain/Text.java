package mingzuozhibi.discspider.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mingzuozhibi.common.BaseModel;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import java.time.Instant;

@Entity
@Setter
@Getter
@NoArgsConstructor
public class Text extends BaseModel {

    @Column(length = 100, unique = true, nullable = false)
    private String name;

    @Lob
    @Column
    private String content;

    @Column
    private Instant createOn;

    @Column
    private Instant updateOn;

    public Text(String name, String content) {
        this.name = name;
        this.content = content;
        this.createOn = Instant.now();
        this.updateOn = this.createOn;
    }

}
