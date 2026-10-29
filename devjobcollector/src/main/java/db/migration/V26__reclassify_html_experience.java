package db.migration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import kr.itsdev.devjobcollector.domain.ExperienceClassification;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V26__reclassify_html_experience extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        long afterId = 0;
        while (true) {
            long lastId = afterId;
            try (PreparedStatement select = context.getConnection().prepareStatement(
                    "SELECT id, source_platform, experience, title, apply_qual FROM job_posts WHERE id > ? ORDER BY id LIMIT 500");
                 PreparedStatement update = context.getConnection().prepareStatement(
                    "UPDATE job_posts SET experience_category=?, experience_label=?, experience_evidence=?, experience_method=? WHERE id=?")) {
                select.setLong(1, afterId);
                try (ResultSet rows = select.executeQuery()) {
                    while (rows.next()) {
                        lastId = rows.getLong("id");
                        var result = ExperienceClassification.classify(rows.getString("experience"), rows.getString("title"),
                                rows.getString("apply_qual"), "COMPANY_DIRECT".equals(rows.getString("source_platform")));
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
