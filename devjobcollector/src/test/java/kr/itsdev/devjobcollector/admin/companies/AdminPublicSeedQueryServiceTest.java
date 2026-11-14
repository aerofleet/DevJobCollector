package kr.itsdev.devjobcollector.admin.companies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class AdminPublicSeedQueryServiceTest {
    @Test
    void calculatesAndClampsPageAgainstCurrentCount() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(47L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var result = new AdminPublicSeedQueryService(jdbc).page(99, 20);
        assertThat(result.totalElements()).isEqualTo(47);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(2);
        verify(jdbc).query(contains("LIMIT ? OFFSET ?"), any(RowMapper.class), eq(20), eq(40L));
    }

    @Test
    void handlesEmptyListAndRejectsInvalidParameters() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var service = new AdminPublicSeedQueryService(jdbc);
        assertThat(service.page(0, 20).totalPages()).isZero();
        assertThatThrownBy(() -> service.page(-1, 20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.page(0, 101)).isInstanceOf(ResponseStatusException.class);
    }
}
