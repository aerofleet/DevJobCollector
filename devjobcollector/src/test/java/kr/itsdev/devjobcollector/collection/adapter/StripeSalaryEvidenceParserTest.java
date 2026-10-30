package kr.itsdev.devjobcollector.collection.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.assertThat;

class StripeSalaryEvidenceParserTest {
    private final StripeSalaryEvidenceParser parser = new StripeSalaryEvidenceParser(new ObjectMapper());
    private final String html = """
            <script type="application/ld+json">{"@type":"JobPosting","identifier":{"value":"8330801"}}</script>
            <div class="careers-listing-details__body"><h2>Pay and benefits</h2>
            <div class="careers-listing-details__body-content">
            <p>The annual salary range for the primary location is A$306,500 - A$459,700. For sales roles this is on target earnings including commissions and base salary.</p>
            <p>Benefits vary by location.</p></div></div>
            """;

    @Test void retainsCompleteSourceParagraphsForMatchingJob() {
        var result = parser.parse(html, "8330801");
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(result.paragraphs()).hasSize(2);
        assertThat(result.paragraphs().getFirst()).contains("A$306,500 - A$459,700", "commissions and base salary", "primary location");
    }

    @Test void rejectsMismatchedMalformedAndMultiplePostings() {
        assertThat(parser.parse(html, "8330802").status()).isEqualTo("UNAVAILABLE");
        assertThat(parser.parse(html + "<script type='application/ld+json'>{bad}</script>", "8330801").status()).isEqualTo("UNAVAILABLE");
        assertThat(parser.parse(html + "<script type='application/ld+json'>{\"@type\":\"JobPosting\",\"identifier\":{\"value\":\"2\"}}</script>", "8330801").status()).isEqualTo("UNAVAILABLE");
    }

    @Test void distinguishesMissingAndDuplicatePaySections() {
        assertThat(parser.parse(html.replace("Pay and benefits", "Benefits"), "8330801").status()).isEqualTo("UNAVAILABLE");
        assertThat(parser.parse(html + "<div class='careers-listing-details__body'><h2>Pay and benefits</h2></div>", "8330801").status()).isEqualTo("AMBIGUOUS");
        assertThat(parser.parse(html.replace("The annual salary range for the primary location is A$306,500 - A$459,700. For sales roles this is on target earnings including commissions and base salary.", "Benefits vary by location."), "8330801").status()).isEqualTo("UNAVAILABLE");
    }

    @Test void verifiesCapturedStripePageWhenFixtureIsExplicitlyProvided() throws Exception {
        String fixture = System.getenv("DJC_STRIPE_TEST_FIXTURE");
        assumeTrue(fixture != null && !fixture.isBlank());
        var result = parser.parse(Files.readString(Path.of(fixture)), "8330801");
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(String.join(" ", result.paragraphs())).contains("A$306,500", "A$459,700", "On Target Earnings", "primary location");
    }
}
