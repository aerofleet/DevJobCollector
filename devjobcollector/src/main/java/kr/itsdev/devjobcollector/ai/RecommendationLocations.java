package kr.itsdev.devjobcollector.ai;

import java.util.List;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.repository.JobLocationFilter;

/** Preference matching is a ranking signal, not proof of work authorization or remote eligibility. */
public final class RecommendationLocations {
    public static final String OPTIONS = "국내 전체|전국|서울|경기|인천|부산|대구|대전|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주|해외 전체|일본|중국|대만|홍콩|싱가포르|베트남|태국|말레이시아|인도네시아|필리핀|인도|미국|캐나다|멕시코|브라질|영국|독일|프랑스|네덜란드|아일랜드|스위스|스웨덴|스페인|폴란드|호주|뉴질랜드|아랍에미리트|사우디아라비아|이스라엘|원격";

    static boolean matches(String actual, List<String> preferred) {
        if (actual == null || actual.isBlank()) return false;
        return preferred.stream().anyMatch(value -> {
            String key = switch (value) {
                case "국내 전체" -> "domestic";
                case "해외 전체" -> "overseas";
                case "원격" -> "Remote";
                default -> value;
            };
            String pattern = JobLocationFilter.pattern(key);
            return pattern != null && Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(actual).find();
        });
    }

    private RecommendationLocations() {}
}
