package kr.itsdev.devjobcollector.ai;

import java.util.List;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.repository.JobLocationFilter;

/** Selected locations constrain candidates; matching is not proof of work authorization. */
public final class RecommendationLocations {
    public static final String OPTIONS = "국내 전체|전국|서울|경기|인천|부산|대구|대전|광주|울산|세종|강원|충북|충남|전북|전남|경북|경남|제주|해외 전체|일본|중국|대만|홍콩|싱가포르|베트남|태국|말레이시아|인도네시아|필리핀|인도|미국|캐나다|멕시코|브라질|영국|독일|프랑스|네덜란드|아일랜드|스위스|스웨덴|스페인|폴란드|호주|뉴질랜드|아랍에미리트|사우디아라비아|이스라엘|원격";

    static final List<String> DOMESTIC_REGIONS = List.of("서울", "경기", "인천", "부산", "대구", "대전", "광주", "울산", "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    static String pattern(List<String> preferred) {
        var selections = new java.util.ArrayList<>(preferred);
        // Remote is a work arrangement, not permission to bypass selected geography.
        // Only a remote-only preference searches remote jobs without a geographic scope.
        if (selections.stream().anyMatch(value -> !value.equals("원격"))) selections.removeIf("원격"::equals);
        if (preferred.containsAll(DOMESTIC_REGIONS)) selections.add("국내 전체");
        return selections.stream().map(value -> {
            String key = switch (value) {
                case "국내 전체", "전국" -> "domestic";
                case "해외 전체" -> "overseas";
                case "원격" -> "Remote";
                default -> value;
            };
            return JobLocationFilter.pattern(key);
        }).filter(java.util.Objects::nonNull).distinct().map(value -> "(" + value + ")")
                .collect(java.util.stream.Collectors.joining("|"));
    }

    static boolean matches(String actual, List<String> preferred) {
        if (actual == null || actual.isBlank()) return false;
        String pattern = pattern(preferred);
        return !pattern.isEmpty() && Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(actual).find();
    }

    static boolean eligible(String actual, List<String> preferred) {
        return preferred.isEmpty() || matches(actual, preferred);
    }

    private RecommendationLocations() {}
}
