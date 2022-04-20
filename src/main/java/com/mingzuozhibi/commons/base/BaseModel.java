package com.mingzuozhibi.commons.base;

import com.mingzuozhibi.commons.gson.GsonIgnored;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
//@MappedSuperclass
public abstract class BaseModel implements Serializable {

    //    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //    @Version
    @GsonIgnored
    private Long version;

}