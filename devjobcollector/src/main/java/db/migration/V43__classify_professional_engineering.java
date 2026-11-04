package db.migration;

import java.sql.PreparedStatement;
import kr.itsdev.devjobcollector.domain.ExperienceClassification;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Reclassify unreviewed unknown postings containing the newly supported wording. */
public class V43__classify_professional_engineering extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        long after = 0;
        while (true) {
            long last = after;
            try (PreparedStatement select = context.getConnection().prepareStatement(
                    "SELECT id,source_platform,experience,title,apply_qual FROM job_posts WHERE id>? AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL AND apply_qual LIKE '%professional%engineering%building%' ORDER BY id LIMIT 500");
                 PreparedStatement update = context.getConnection().prepareStatement(
                    "UPDATE job_posts SET experience_category=?,experience_label=?,experience_evidence=?,experience_method=?,version=version+1 WHERE id=? AND experience_category='UNKNOWN' AND reviewed_experience_category IS NULL")) {
                select.setLong(1, after);
                try (var rows = select.executeQuery()) {
                    while (rows.next()) {
                        last = rows.getLong("id");
                        var result = ExperienceClassification.classify(rows.getString("experience"), rows.getString("title"), rows.getString("apply_qual"),
                                "COMPANY_DIRECT".equals(rows.getString("source_platform")));
                        if (!"RULE".equals(result.method()) || !"EXPERIENCED".equals(result.category())) continue;
                        update.setString(1, result.category()); update.setString(2, result.label());
                        update.setString(3, result.evidence()); update.setString(4, result.method());
                        update.setLong(5, last); update.addBatch();
                    }
                }
                update.executeBatch();
            }
            if (last == after) return;
            after = last;
        }
    }
}
