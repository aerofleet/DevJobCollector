package kr.itsdev.devjobcollector.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Recognized location labels only: unknown text is not assumed to be overseas. */
final class JobLocationFilter {
    private static final Map<String, List<String>> DOMESTIC = new LinkedHashMap<>();
    private static final Map<String, List<String>> OVERSEAS = new LinkedHashMap<>();
    static {
        add(DOMESTIC, "서울", "Seoul");
        add(DOMESTIC, "경기", "Gyeonggi", "수원", "성남", "판교");
        add(DOMESTIC, "인천", "Incheon");
        add(DOMESTIC, "부산", "Busan");
        add(DOMESTIC, "대구", "Daegu");
        add(DOMESTIC, "대전", "Daejeon");
        add(DOMESTIC, "광주", "Gwangju");
        add(DOMESTIC, "울산", "Ulsan");
        add(DOMESTIC, "세종", "Sejong");
        add(DOMESTIC, "강원", "Gangwon");
        add(DOMESTIC, "충북", "충청북", "Chungbuk", "Chungcheongbuk");
        add(DOMESTIC, "충남", "충청남", "Chungnam", "Chungcheongnam");
        add(DOMESTIC, "전북", "전라북", "Jeonbuk", "Jeollabuk");
        add(DOMESTIC, "전남", "전라남", "Jeonnam", "Jeollanam");
        add(DOMESTIC, "경북", "경상북", "Gyeongbuk", "Gyeongsangbuk");
        add(DOMESTIC, "경남", "경상남", "Gyeongnam", "Gyeongsangnam");
        add(DOMESTIC, "제주", "Jeju");
        add(DOMESTIC, "전국", "전지역", "전 지역");
        add(OVERSEAS, "일본", "Japan", "JP");
        add(OVERSEAS, "중국", "China", "CN");
        add(OVERSEAS, "대만", "Taiwan", "TW");
        add(OVERSEAS, "홍콩", "Hong Kong", "HK");
        add(OVERSEAS, "싱가포르", "Singapore", "SG");
        add(OVERSEAS, "베트남", "Vietnam", "Viet Nam", "VN");
        add(OVERSEAS, "태국", "Thailand", "TH");
        add(OVERSEAS, "말레이시아", "Malaysia", "MY");
        add(OVERSEAS, "인도네시아", "Indonesia", "ID");
        add(OVERSEAS, "필리핀", "Philippines", "PH");
        add(OVERSEAS, "인도", "India", "IN");
        add(OVERSEAS, "미국", "United States", "USA", "US", "U.S.", "U.S.A.");
        add(OVERSEAS, "캐나다", "Canada");
        add(OVERSEAS, "멕시코", "Mexico", "MX");
        add(OVERSEAS, "브라질", "Brazil", "BR");
        add(OVERSEAS, "영국", "United Kingdom", "UK", "England", "Scotland", "Wales", "Northern Ireland");
        add(OVERSEAS, "독일", "Germany", "Deutschland", "DE");
        add(OVERSEAS, "프랑스", "France", "FR");
        add(OVERSEAS, "네덜란드", "Netherlands", "NL");
        add(OVERSEAS, "아일랜드", "Ireland");
        add(OVERSEAS, "스위스", "Switzerland", "CH");
        add(OVERSEAS, "스웨덴", "Sweden", "SE");
        add(OVERSEAS, "스페인", "Spain", "ES");
        add(OVERSEAS, "폴란드", "Poland", "PL");
        add(OVERSEAS, "호주", "Australia", "AU");
        add(OVERSEAS, "뉴질랜드", "New Zealand", "NZ");
        add(OVERSEAS, "아랍에미리트", "United Arab Emirates", "UAE");
        add(OVERSEAS, "사우디아라비아", "Saudi Arabia");
        add(OVERSEAS, "이스라엘", "Israel");
    }

    private static void add(Map<String, List<String>> map, String key, String... aliases) {
        map.put(key, Stream.concat(Stream.of(key), Stream.of(aliases)).toList());
    }

    static String pattern(String value) {
        if (value == null || value.isBlank()) return null;
        String key = value.trim();
        Stream<String> terms;
        if (key.equals("domestic")) {
            terms = Stream.concat(DOMESTIC.values().stream().flatMap(List::stream),
                    Stream.of("국내", "대한민국", "한국", "South Korea", "Republic of Korea"));
        } else if (key.equals("overseas")) {
            terms = Stream.concat(OVERSEAS.values().stream().flatMap(List::stream),
                    Stream.of("해외", "국외", "overseas", "abroad", "Europe", "Asia", "Oceania"));
        } else if (key.equalsIgnoreCase("Remote")) {
            terms = Stream.of("Remote", "원격", "재택", "Work from home", "WFH");
        } else {
            List<String> aliases = DOMESTIC.getOrDefault(key, OVERSEAS.get(key));
            if (aliases == null) return null;
            terms = aliases.stream();
        }
        return terms.distinct().map(JobLocationFilter::literalPattern).collect(Collectors.joining("|"));
    }

    private static String literalPattern(String term) {
        String escaped = term.toLowerCase(Locale.ROOT).replace(".", "\\.");
        // Two-letter codes must be a complete location segment, not prose such as "work in Seoul".
        if (term.matches("[A-Z]{2}")) return "(^|[,;/()] *)" + escaped + "( *[,;/()]|$)";
        // Bound Latin names to avoid USA matching Busan, or US matching Australia.
        if (term.matches(".*[A-Za-z].*")) return "(^|[^a-z])" + escaped + "([^a-z]|$)";
        // India must not include Indonesia; Korean country labels otherwise match address prefixes.
        if (term.equals("인도")) return "인도($|[^네])";
        return escaped;
    }
}
