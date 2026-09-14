package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnonymousRankQueryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AnonymousRankQueryService service;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;
    private final long parent=1874244142494692001L, student=1874244142494692002L, classroom=1874244142494692003L;
    private AuthenticatedUser user(long id) { return new AuthenticatedUser(id,id+9,"rank","家长",AuthClientType.WEB,List.of("PARENT")); }
    @BeforeEach void seed() {
        for (long p : List.of(parent,parent+20)) {
            jdbc.update("insert into sys_user(id,username,display_name,user_type,status) values(?,?,'家长','FAMILY','ENABLED')",p,"rank_"+p);
            jdbc.update("insert into sys_user_role(id,user_id,role_id,organization_scope_key) select ?,?,id,'GLOBAL' from sys_role where role_code='PARENT'",p+100,p);
            jdbc.update("insert into edu_student(id,student_name,status) select ?,'孩子','ENABLED' where not exists(select 1 from edu_student where id=?)",student,student);
            jdbc.update("insert into edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) values(?,?,?,?,'ACTIVE',?)",p+200,p,student,p==parent?"PRIMARY_GUARDIAN":"SECONDARY_GUARDIAN",p==parent?"PRIMARY":"SECONDARY");
        }
        jdbc.update("insert into sys_organization(id,organization_code,organization_name,organization_type,organization_path,status) values(?,'RANK_QUERY_CLASS','排名查询测试班','CLASS','/RANK_QUERY/','ENABLED')",classroom);
        jdbc.update("insert into edu_student_organization(id,student_id,organization_id,relation_type,status) values(?,?,?,'CLASS','ACTIVE')",parent+300,student,classroom);
        jdbc.update("update sys_feature_toggle set status='ENABLED' where feature_code='ANONYMOUS_CLASS_RANK'");
    }
    @Test void defaultsOffAndRequiresOwnPreferenceBeforeReading() {
        assertThat(service.preference(user(parent),student,classroom).enabled()).isFalse();
        assertThatThrownBy(() -> service.ranking(user(parent),student,classroom)).isInstanceOf(RuntimeException.class);
        var enabled=service.set(user(parent),student,classroom,true,0);
        assertThat(enabled.version()).isEqualTo(1);
        assertThat(service.ranking(user(parent),student,classroom)).hasSize(1);
        assertThat(service.preference(user(parent+20),student,classroom).enabled()).isFalse();
        assertThatThrownBy(() -> service.ranking(user(parent+20),student,classroom)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select id from growth_rank_preference where parent_user_id=?",Long.class,parent).toString()).hasSize(19);
    }
    @Test void miniappRequiresItsOwnPermissionAndCannotBorrowWebGrant() {
        var mini = new AuthenticatedUser(parent,parent+9,"rank","家长",AuthClientType.MINIAPP,List.of("PARENT"));
        assertThat(service.set(mini,student,classroom,true,0).enabled()).isTrue();
        assertThat(service.ranking(mini,student,classroom)).hasSize(1);
        jdbc.update("update sys_permission set status='DISABLED' where permission_code='MINIAPP_ANONYMOUS_CLASS_RANK_READ'");
        session.clearCache();
        assertThatThrownBy(() -> service.ranking(mini,student,classroom)).isInstanceOf(RuntimeException.class);
        assertThat(service.ranking(user(parent),student,classroom)).hasSize(1);
        assertThat(service.set(mini,student,classroom,false,1).enabled()).isFalse();
    }
    @Test void closesIdempotentlyAndRejectsStaleVersion() {
        service.set(user(parent),student,classroom,true,0);
        assertThat(service.set(user(parent),student,classroom,true,0).version()).isEqualTo(1);
        assertThatThrownBy(() -> service.set(user(parent),student,classroom,false,0)).isInstanceOf(IllegalStateException.class);
        assertThat(service.set(user(parent),student,classroom,false,1).version()).isEqualTo(2);
        assertThatThrownBy(() -> service.ranking(user(parent),student,classroom)).isInstanceOf(RuntimeException.class);
    }
    @Test void revokedRelationOrClassBlocksReadingButOwnerCanWithdraw() {
        service.set(user(parent),student,classroom,true,0);
        jdbc.update("update edu_student_organization set status='INACTIVE' where student_id=?",student);
        session.clearCache();
        assertThatThrownBy(() -> service.ranking(user(parent),student,classroom)).isInstanceOf(RuntimeException.class);
        jdbc.update("delete from edu_parent_student where parent_user_id=?",parent);
        jdbc.update("update sys_feature_toggle set status='DISABLED' where feature_code='ANONYMOUS_CLASS_RANK'");
        session.clearCache();
        assertThat(service.withdrawals(user(parent))).containsExactly(
                new AnonymousRankQueryService.WithdrawalOption(String.valueOf(student),String.valueOf(classroom),1));
        assertThat(service.withdrawals(user(parent+20))).isEmpty();
        assertThat(service.set(user(parent),student,classroom,false,1).enabled()).isFalse();
        assertThat(service.withdrawals(user(parent))).isEmpty();
        assertThatThrownBy(() -> service.set(user(parent),student,classroom,true,2)).isInstanceOf(RuntimeException.class);
    }
}
