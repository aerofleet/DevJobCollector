package kr.itsdev.devjobcollector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "data-api.public-data")
@Getter
@Setter
public class PublicDataProperties {
    private String baseUrl;
    private String serviceKey;
}
