package com.mingzuozhibi.history;

import lombok.*;

import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
public class History {
    private String asin;
    private String type;
    private String title;
    private Instant createOn;
}
