package com.lingdong.learning.learningtask.application;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherClassChangeTransactionTest {
    @Test void laterFailureRollsBackEarlierClassWrites() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:teacher_delta;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("create table if not exists test_teacher_delta(id bigint primary key)");
        jdbc.update("delete from test_teacher_delta");
        var service = mock(TeacherClassAssignmentService.class, CALLS_REAL_METHODS);
        doAnswer(call -> { jdbc.update("insert into test_teacher_delta values (?)", (Long)call.getArgument(2)); return null; })
                .when(service).assign(null, 1L, 2L);
        doThrow(new IllegalArgumentException("out of scope")).when(service).remove(null, 1L, 3L);
        var factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new MatchAlwaysTransactionAttributeSource()));
        var proxied = (TeacherClassAssignmentService)factory.getProxy();
        assertThrows(IllegalArgumentException.class, () -> proxied.changeClasses(null, 1L, List.of(2L), List.of(3L)));
        assertEquals(0, jdbc.queryForObject("select count(*) from test_teacher_delta", Integer.class));
        proxied.changeClasses(null, 1L, List.of(2L, 2L), List.of());
        assertEquals(1, jdbc.queryForObject("select count(*) from test_teacher_delta", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> proxied.changeClasses(null, 1L, List.of(2L), List.of(2L)));
    }
}
