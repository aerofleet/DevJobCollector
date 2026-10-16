package kr.itsdev.devjobcollector.repository;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

class JobLocationFilterTest {
    private boolean matches(String filter, String location) {
        return Pattern.compile(JobLocationFilter.pattern(filter)).matcher(location.toLowerCase(Locale.ROOT)).find();
    }

    @Test
    void matchesDomesticAbbreviationsAndEnglishNames() {
        assertThat(matches("충북", "충청북도 청주시")).isTrue();
        assertThat(matches("전북", "전북특별자치도")).isTrue();
        assertThat(matches("강원", "Gangwon-do")).isTrue();
        assertThat(matches("서울", "Seoul, South Korea")).isTrue();
        assertThat(matches("domestic", "Republic of Korea")).isTrue();
    }

    @Test
    void matchesCountriesWithoutSubstringOrProseFalsePositives() {
        assertThat(matches("미국", "San Francisco, US")).isTrue();
        assertThat(matches("미국", "United States")).isTrue();
        assertThat(matches("미국", "USA")).isTrue();
        assertThat(matches("미국", "Busan")).isFalse();
        assertThat(matches("미국", "Australia")).isFalse();
        assertThat(matches("인도", "인도네시아 자카르타")).isFalse();
        assertThat(matches("인도", "India")).isTrue();
        assertThat(matches("overseas", "Work in Seoul")).isFalse();
        assertThat(matches("overseas", "근무지 미정")).isFalse();
        assertThat(matches("overseas", "Remote")).isFalse();
        assertThat(matches("overseas", "Berlin, Germany")).isTrue();
    }

    @Test
    void remoteAndUnknownLegacyLocationsRemainSupported() {
        assertThat(matches("Remote", "재택 근무")).isTrue();
        assertThat(matches("Remote", "Work from home")).isTrue();
        assertThat(JobLocationFilter.pattern("춘천")).isNull();
        assertThat(JobLocationFilter.pattern(null)).isNull();
        assertThat(JobLocationFilter.pattern(" ")).isNull();
    }
}
