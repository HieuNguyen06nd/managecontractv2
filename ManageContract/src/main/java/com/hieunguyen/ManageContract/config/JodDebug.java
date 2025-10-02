package com.hieunguyen.ManageContract.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JodDebug {
    @Value("${jodconverter.local.enabled:false}")
    boolean enabled;

    @Value("${jodconverter.local.office-home:}")
    String officeHome;

    @PostConstruct
    void check() {
        log.info("JOD enabled={}, officeHome='{}'", enabled, officeHome);
    }
}

