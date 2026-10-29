package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.domain.ExperienceClassification;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.web.util.HtmlUtils;

public class V30__classify_explicit_recruiting_requirements extends BaseJavaMigration {
    private static final Pattern HEADING = Pattern.compile("(?i)we['’]d love to hear from you if you have");

    @Override
    public void migrate(Context context) throws Exception {
        long afterId = 0;
        while (true) {
            long lastId = afterId;
            try (PreparedStatement select = context.getConnection().prepareStatement(
                    "SELECT id, source_platform, experience, title, apply_qual FROM job_posts WHERE id > ? AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL ORDER BY id LIMIT 500");
                 PreparedStatement update = context.getConnection().prepareStatement(
                    "UPDATE job_posts SET experience_category=?, experience_label=?, experience_evidence=?, experience_method=? WHERE id=? AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL")) {
                select.setLong(1, afterId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        lastId = rows.getLong("id");
                        String description = rows.getString("apply_qual");
                        if (description == null || !HEADING.matcher(HtmlUtils.htmlUnescape(HtmlUtils.htmlUnescape(description))).find()) continue;
                        var result = ExperienceClassification.classify(rows.getString("experience"), rows.getString("title"), description,
                                "COMPANY_DIRECT".equals(rows.getString("source_platform")));
                        if (!"RULE".equals(result.method()) || !"EXPERIENCED".equals(result.category())) continue;
                        update.setString(1, result.category());
                        update.setString(2, result.label());
                        update.setString(3, result.evidence());
                        update.setString(4, result.method());
                        update.setLong(5, lastId);
                        update.addBatch();
                    }
                }
                update.executeBatch();
            }
            if (lastId == afterId) return;
            afterId = lastId;
        }
    }
}
