package kr.itsdev.devjobcollector.service;

import kr.itsdev.devjobcollector.config.PublicDataProperties;
import kr.itsdev.devjobcollector.dto.PublicDataDetailResponse;
import kr.itsdev.devjobcollector.dto.PublicDataListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class PublicDataApiClient {
    private final RestTemplate restTemplate;
    private final PublicDataProperties properties;

        @Value("${data-api.public-data.base-url}")
        private String baseUrl;

        @Value("${data-api.public-data.service-key}")
        private String serviceKey;

        /**
         * 공공데이터 목록 API를 호출한다. [/list]
         */
        public PublicDataListResponse fetchJobList(int page, int size) {
            URI uri = UriComponentsBuilder.fromUriString(properties.getBaseUrl() + "/list")
                    .queryParam("serviceKey", properties.getServiceKey())
                    .queryParam("numOfRows", size)
                    .queryParam("pageNo", page + 1)
                    .queryParam("resultType", "json")
                    .build(true) // 서비스키 인코딩 문제 방지를 위해 true 설정
                    .toUri();

            return restTemplate.getForObject(uri, PublicDataListResponse.class);
        }

        /**
         * 특정 공고의 상세 정보를 호출한다. [/detail]
         */
        public PublicDataDetailResponse fetchJobDetail(String sn) {
            URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/detail")
                    .queryParam("serviceKey", properties.getServiceKey())
                    .queryParam("sn", sn)
                    .queryParam("resultType", "json")
                    .build(true)
                    .toUri();

            return restTemplate.getForObject(uri, PublicDataDetailResponse.class);
        }    
}
