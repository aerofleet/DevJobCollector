package kr.itsdev.devjobcollector.growth;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import kr.itsdev.devjobcollector.config.QuerydslConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named="DJC_MIGRATION_TEST_URL",matches=".+")
@Import({QuerydslConfig.class,GrowthEventService.class})
class GrowthIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GrowthEventService service;
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",()->System.getenv("DJC_MIGRATION_TEST_URL"));
        r.add("spring.datasource.username",()->System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"));
        r.add("spring.datasource.password",()->System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""));
        r.add("spring.flyway.user",()->System.getenv().getOrDefault("DJC_MIGRATION_TEST_USERNAME","root"));
        r.add("spring.flyway.password",()->System.getenv().getOrDefault("DJC_MIGRATION_TEST_PASSWORD",""));
    }
    @BeforeEach void clear() { jdbc.update("DELETE FROM growth_events"); }
    @Test void repeatedDeliveryCountsOnce() {
        var e = new GrowthEventController.Event(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),GrowthEventController.Kind.PAGE_VIEW,"home",null,"velog","social","launch");
        service.record(e); service.record(e);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_events",Integer.class)).isEqualTo(1);
    }
    private void event(UUID visitor, UUID session, String kind, int daysAgo) {
        jdbc.update("INSERT INTO growth_events VALUES(?,?,?,?,?,?,?,?,?,UTC_DATE() - INTERVAL ? DAY + INTERVAL 1 HOUR)",UUID.randomUUID().toString(),visitor.toString(),session.toString(),kind,"home",null,"velog","social","launch",daysAgo);
    }
    @Test void activationUsesDistinctSessionsNotClickCount() {
        UUID visitor=UUID.randomUUID(), session=UUID.randomUUID();
        event(visitor,session,"PAGE_VIEW",0); event(visitor,session,"APPLY_CLICK",0); event(visitor,session,"APPLY_CLICK",0);
        event(visitor,UUID.randomUUID(),"PAGE_VIEW",0);
        var rows=(List<Map<String,Object>>)service.summary().get("channels");
        assertThat(((Number)rows.getFirst().get("sessions")).intValue()).isEqualTo(2);
        assertThat(((Number)rows.getFirst().get("activatedSessions")).intValue()).isEqualTo(1);
    }
    @Test void d7ExcludesImmatureCohortsAndCountsExactDay() {
        UUID returned=UUID.randomUUID(), missed=UUID.randomUUID(), immature=UUID.randomUUID();
        event(returned,UUID.randomUUID(),"PAGE_VIEW",10); event(returned,UUID.randomUUID(),"PAGE_VIEW",3);
        event(missed,UUID.randomUUID(),"PAGE_VIEW",10); event(missed,UUID.randomUUID(),"PAGE_VIEW",2);
        event(immature,UUID.randomUUID(),"PAGE_VIEW",2);
        var retention=(Map<String,Object>)service.summary().get("retention");
        assertThat(((Number)retention.get("eligibleVisitors")).intValue()).isEqualTo(2);
        assertThat(((Number)retention.get("returnedVisitors")).intValue()).isEqualTo(1);
    }
    @Test void retentionDeletesOldRowsOnly() {
        event(UUID.randomUUID(),UUID.randomUUID(),"PAGE_VIEW",91);
        event(UUID.randomUUID(),UUID.randomUUID(),"PAGE_VIEW",1);
        service.purgeExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM growth_events",Integer.class)).isEqualTo(1);
    }
}
