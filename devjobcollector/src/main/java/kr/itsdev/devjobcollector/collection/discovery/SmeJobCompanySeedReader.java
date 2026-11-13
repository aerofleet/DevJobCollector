package kr.itsdev.devjobcollector.collection.discovery;

import java.io.IOException;
import java.io.Reader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** Reads the public KOSME hiring-company CSV as leads; no row is treated as a live job. */
@Component
public class SmeJobCompanySeedReader {
    private static final Set<String> REQUIRED = Set.of("기업명", "사업자번호", "지역구분", "종업원수", "공고명", "고용형태", "공고등록일");
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader().setSkipHeaderRecord(true).setDuplicateHeaderMode(org.apache.commons.csv.DuplicateHeaderMode.DISALLOW)
            .get();

    /** Caller supplies a correctly decoded reader and handles the lifecycle of emitted leads. */
    public Counts read(Reader input, Consumer<Seed> sink) throws IOException {
        int accepted = 0, skipped = 0;
        PushbackReader decoded = new PushbackReader(input, 1);
        int first = decoded.read();
        if (first != -1 && first != '\ufeff') decoded.unread(first);
        try (CSVParser parser = FORMAT.parse(decoded)) {
            Set<String> headers = parser.getHeaderMap().keySet();
            if (!headers.containsAll(REQUIRED)) throw new IOException("Unexpected KOSME CSV headers");
            for (CSVRecord row : parser) {
                if (!row.isConsistent()) { skipped++; continue; }
                String name = row.get("기업명").trim();
                String number = row.get("사업자번호").trim();
                String title = row.get("공고명").trim();
                String registered = row.get("공고등록일").trim();
                if (name.isEmpty() || name.length() > 200 || !number.matches("[0-9]{3}-?[0-9]{2}-?[0-9]{5}")
                        || title.length() > 500 || registered.length() > 30) { skipped++; continue; }
                sink.accept(new Seed(name, hash(number.replace("-", "")), title, registered));
                accepted++;
            }
        }
        return new Counts(accepted, skipped);
    }

    private static String hash(String number) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(number.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Seed(String companyName, String businessNumberHash, String jobTitle, String postingDateRaw) { }
    public record Counts(int accepted, int skipped) { }
}
