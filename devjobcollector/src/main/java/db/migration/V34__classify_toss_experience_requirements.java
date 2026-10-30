package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.regex.Pattern;
import kr.itsdev.devjobcollector.domain.ExperienceClassification;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Backfills only unreviewed, unknown company-page postings with the recognized heading. */
public class V34__classify_toss_experience_requirements extends BaseJavaMigration {
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s*이런\\s*분과\\s*함께하고\\s*싶어요\\s*$");

    @Override public void migrate(Context context) throws Exception {
        long afterId = 0;
        while (true) {
            long lastId = afterId;
            try (PreparedStatement select = context.getConnection().prepareStatement(
                    "SELECT id, experience, title, apply_qual FROM job_posts WHERE id>? AND source_platform='COMPANY_PAGE' AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL ORDER BY id LIMIT 500");
                 PreparedStatement update = context.getConnection().prepareStatement(
                    "UPDATE job_posts SET experience_category=?, experience_label=?, experience_evidence=?, experience_method=?, version=version+1 WHERE id=? AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL")) {
                select.setLong(1, afterId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        lastId = rows.getLong("id");
                        String body = rows.getString("apply_qual");
                        if (body == null || !HEADING.matcher(body).find()) continue;
                        var result = ExperienceClassification.classify(rows.getString("experience"), rows.getString("title"), body, false);
                        if (!"RULE".equals(result.method()) || !"EXPERIENCED".equals(result.category())) continue;
                        update.setString(1, result.category()); update.setString(2, result.label());
                        update.setString(3, result.evidence()); update.setString(4, result.method());
                        update.setLong(5, lastId); update.addBatch();
                    }
                }
                update.executeBatch();
            }
            if (lastId == afterId) return;
            afterId = lastId;
        }
    }
}
