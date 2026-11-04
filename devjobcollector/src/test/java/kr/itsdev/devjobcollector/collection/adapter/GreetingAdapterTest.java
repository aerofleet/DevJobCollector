package kr.itsdev.devjobcollector.collection.adapter;

import org.junit.jupiter.api.Test;
import kr.itsdev.devjobcollector.collection.domain.CollectionStatus;
import kr.itsdev.devjobcollector.collection.domain.SourceType;
import kr.itsdev.devjobcollector.collection.domain.CollectionTier;
import kr.itsdev.devjobcollector.collection.domain.CompanySourceTarget;
import kr.itsdev.devjobcollector.collection.dto.JobRawDto;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class GreetingAdapterTest {
    @Test void capturesBracketedNuuaSections() {
        String html = "<p>회사 소개</p><p>[주요업무]</p><p>• LangChain 개발</p><p>[자격요건]</p><p>• 3년 이상의 백엔드 경험</p><p>[우대사항]</p><p>• Python 경험</p>";
        assertThat(GreetingAdapter.extractRelevantText(html))
                .contains("[주요업무]", "LangChain", "[자격요건]", "[우대사항]").doesNotContain("회사 소개");
    }
    @Test void schemaUpgradeSchedulesARefreshButCurrentVersionRetainsItsSchedule() {
        var target = new CompanySourceTarget(null, "누아", SourceType.GREETING, "nuua", "https://nuua.career.greetinghr.com/ko/home", CollectionTier.B);
        target.activate();
        Instant next = Instant.parse("2030-01-01T00:00:00Z");
        target.recordSuccess(Instant.now(), 200, "greeting-openings-v1", next);
        target.requestCollectionForSchema(GreetingAdapter.VERSION);
        assertThat(target.getNextCollectAt()).isNull();
        target.recordSuccess(Instant.now(), 200, GreetingAdapter.VERSION, next);
        target.requestCollectionForSchema(GreetingAdapter.VERSION);
        assertThat(target.getNextCollectAt()).isEqualTo(next);
    }
    @Test void extractsOpenPostingDetailsAndIgnoresApplicationData() throws Exception {
        String html = """
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"dehydratedState":{"queries":[{"queryKey":["career","getOpeningById",{"openingId":87703}],"state":{"data":{"success":true,"data":{"openingsInfo":{"openingId":87703,"status":"OPEN","deploy":true,"detail":"<p>회사 소개</p><p><strong>주요업무</strong></p><p>• 예약 시스템 개발</p><p><strong>자격요건</strong></p><p>• 개발 경력 3년 이상</p><p>• Java, Spring Framework 개발 경험</p><p><strong>우대사항</strong></p><p>• 예약 플랫폼 연동 경험</p>"},"jobPositionSetting":{"jobPositions":[{"workspacePlace":{"place":"대한민국 서울특별시 중구 세종대로 136","detailPlace":"8층"},"jobPositionCareer":{"careerType":"EXPERIENCED","careerFrom":3,"careerTo":null},"jobPositionEmployment":{"employmentType":"FULL_TIME_WORKER"},"workspaceJob":{"job":"백엔드"}}]}}}}}]}}}}</script>
            """;
        var listing = new JobRawDto(SourceType.GREETING, "87703", "웹 백엔드", null, null, null,
                "https://nuua.career.greetinghr.com/o/87703", "https://nuua.career.greetinghr.com/o/87703", null, null, null, "hash", "{}");
        var job = GreetingAdapter.parseDetail(listing, html);
        assertThat(job.location()).contains("서울특별시", "8층");
        assertThat(job.experience()).isEqualTo("경력 3년 이상");
        assertThat(job.employmentType()).isEqualTo("정규직");
        assertThat(job.plainDescription()).contains("자격요건", "Java, Spring Framework", "우대사항").doesNotContain("회사 소개");
        assertThat(GreetingAdapter.parseDetail(listing, html.replace("\"OPEN\"", "\"CLOSED\""))).isNull();
    }
    @Test void collectsPublishedListAndPermitsClosureOnlyWhenRenderedListMatches() throws Exception {
        String html = """
            <a data-testid="공고_아이템" href="/ko/o/123">개발자</a>
            <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"dehydratedState":{"queries":[{"queryKey":["openings"],"state":{"data":[{"deploy":true,"openingId":123,"title":"개발자","openDate":"2026-01-01T00:00:00Z","dueDate":"2026-12-01T00:00:00Z"}]}}]}}}}</script>
            """;
        var result = GreetingAdapter.parseList(html, "https://nuua.career.greetinghr.com", "User-agent: *\nAllow: /");
        assertThat(result.status()).isEqualTo(CollectionStatus.SUCCESS);
        assertThat(result.closureEvaluationAllowed()).isTrue();
        assertThat(result.jobs()).singleElement().satisfies(job -> {
            assertThat(job.title()).isEqualTo("개발자");
            assertThat(job.deadlineAt()).isNotNull();
        });
        var mismatch = GreetingAdapter.parseList(html.replace("/ko/o/123", "/ko/o/456"), "https://nuua.career.greetinghr.com", "User-agent: *\nAllow: /");
        assertThat(mismatch.status()).isEqualTo(CollectionStatus.SCHEMA_CHANGED);
        assertThat(mismatch.closureEvaluationAllowed()).isFalse();
    }
    @Test void acceptsOnlyPostingUrlsOnTheConfiguredBoard() {
        String xml = """
                <urlset><url><loc>https://nuua.career.greetinghr.com/o/123</loc></url>
                <url><loc>https://nuua.career.greetinghr.com/o/123</loc></url>
                <url><loc>https://evil.example/o/456</loc></url>
                <url><loc>https://nuua.career.greetinghr.com/o/123/apply</loc></url></urlset>
                """;
        assertThat(GreetingAdapter.postingUrls(xml, "https://nuua.career.greetinghr.com"))
                .containsExactly("https://nuua.career.greetinghr.com/o/123");
    }

    @Test void respectsRobotsRulesAndSearchProhibition() {
        String robots = "User-agent: *\nAllow: /\nDisallow: /o/*/apply\n";
        assertThat(GreetingAdapter.allowed(robots, "/o/123")).isTrue();
        assertThat(GreetingAdapter.allowed(robots, "/o/123/apply")).isFalse();
        assertThat(GreetingAdapter.allowed("User-agent: *\nContent-Signal: search=no\nAllow: /", "/o/123")).isFalse();
        assertThat(GreetingAdapter.allowed("", "/o/123")).isFalse();
    }
}
