package kr.itsdev.devjobcollector.collection.discovery;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stages public dataset leads independently of registered companies and collection targets. */
@Service
public class SmeJobCompanySeedImporter {
    private final SmeJobCompanySeedReader reader;
    private final JdbcTemplate jdbc;

    public SmeJobCompanySeedImporter(SmeJobCompanySeedReader reader, JdbcTemplate jdbc) {
        this.reader = reader;
        this.jdbc = jdbc;
    }

    @Transactional(rollbackFor = Exception.class)
    public SmeJobCompanySeedReader.Counts importCsv(Reader csv) throws IOException {
        List<SmeJobCompanySeedReader.Seed> batch = new ArrayList<>(500);
        var counts = reader.read(csv, seed -> {
            batch.add(seed);
            if (batch.size() == 500) flush(batch);
        });
        flush(batch);
        return counts;
    }

    private void flush(List<SmeJobCompanySeedReader.Seed> batch) {
        if (batch.isEmpty()) return;
        String value = "('KOSME_JOB_COMPANIES', ?, ?, 'DISCOVERED', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))";
        String sql = "INSERT INTO public_company_seed "
                + "(source, business_number_hash, company_name, status, first_seen_at, last_seen_at) VALUES "
                + String.join(",", Collections.nCopies(batch.size(), value))
                + " ON DUPLICATE KEY UPDATE last_seen_at = UTC_TIMESTAMP(6)";
        List<Object> arguments = new ArrayList<>(batch.size() * 2);
        batch.forEach(seed -> { arguments.add(seed.businessNumberHash()); arguments.add(seed.companyName()); });
        jdbc.update(sql, arguments.toArray());
        batch.clear();
    }
}
