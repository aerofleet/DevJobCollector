package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KakaoDeadlineSourceTest {
    @Test void preservesExplicitPeriodVisibilityAndMissingVisibilitySeparately() throws Exception {
        var mapper = new ObjectMapper();
        var response = mapper.readValue("""
                {"jobList":[{"realId":"S-4432","endDate":null,"hidePostingPeriodFlag":false},
                            {"realId":"S-1","endDate":null,"hidePostingPeriodFlag":true},
                            {"realId":"S-2","endDate":null}],"totalPage":1}
                """, KakaoCareerResponse.class);
        assertThat(response.jobList().get(0).hidePostingPeriodFlag()).isFalse();
        assertThat(response.jobList().get(1).hidePostingPeriodFlag()).isTrue();
        assertThat(response.jobList().get(2).hidePostingPeriodFlag()).isNull();
        assertThat(mapper.readTree(mapper.writeValueAsString(response.jobList().getFirst())).path("hidePostingPeriodFlag").booleanValue()).isFalse();
    }
}
