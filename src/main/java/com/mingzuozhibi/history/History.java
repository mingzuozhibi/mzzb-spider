package com.mingzuozhibi.history;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
public class History {

    private String asin;
    private String type;
    private String title;
    private Instant createOn;

}
