package com.lingdong.learning.student.application;

import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentRelationshipInvitationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ParentRelationshipPagingPersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ParentStudentMapper relationships;
    @Autowired ParentRelationshipInvitationMapper invitations;
    @Autowired ParentRelationshipApplicationService service;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;

    @Test void pagesActiveInactiveAndReceivedRowsWithoutCrossingParentScope() {
        // Enable only inside this rollback-only isolated transaction; production gates remain unchanged.
        assertThat(jdbc.update("UPDATE sys_feature_toggle SET status='ENABLED' WHERE feature_code='PARENT_RELATIONSHIP_MANAGEMENT'")).isPositive();
        long base=System.nanoTime(),parent=base,other=base+1,first=base+2,second=base+3;
        jdbc.update("INSERT INTO sys_user(id,username,display_name,user_type,status) VALUES(?,?,?,'FAMILY','ENABLED'),(?,?,?,'FAMILY','ENABLED')",parent,"paging_parent_"+base,"分页关系家长",other,"paging_other_"+base,"范围外家长");
        jdbc.update("INSERT INTO edu_student(id,student_name,grade_code,status) VALUES(?,'中文孩子甲','GRADE_3','ENABLED'),(?,'中文孩子乙','GRADE_3','ENABLED')",first,second);
        LocalDateTime now=LocalDateTime.now();
        relationships.insertPrimaryAt(base+4,parent,first,now);relationships.insertPrimaryAt(base+5,parent,second,now);
        assertThat(relationships.countActiveStudentsByParent(parent)).isEqualTo(2);
        assertThat(service.getRelationshipStudentsPage(parent,1,1).total()).isEqualTo(2);
        assertThat(service.getRelationshipStudentsPage(parent,1,1).items()).hasSize(1);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.getRelationshipStudentsPage(parent,0,20)).isInstanceOf(IllegalArgumentException.class);
        var one=relationships.findActiveStudentsPageByParent(parent,0,1);var two=relationships.findActiveStudentsPageByParent(parent,1,1);
        assertThat(one).hasSize(1);assertThat(two).hasSize(1);assertThat(one.get(0).studentId()).isNotEqualTo(two.get(0).studentId());
        assertThat(relationships.findActiveStudentsPageByParent(other,0,20)).isEmpty();
        assertThat(relationships.findActiveStudentsPageByParent(parent,2,1)).isEmpty();
        relationships.unbind(base+4,"CLOSED:"+(base+4),now);
        assertThat(relationships.countInactiveByParent(parent)).isEqualTo(1);
        assertThat(service.getInactiveRelationshipsPage(parent,1,20).total()).isEqualTo(1);
        assertThat(relationships.findInactivePageByParent(parent,0,1)).singleElement().satisfies(row->assertThat(row.studentId()).isEqualTo(first));
        assertThat(relationships.countInactiveByParent(other)).isZero();
        for(int n=0;n<2;n++) jdbc.update("INSERT INTO edu_parent_relationship_invitation(id,student_id,inviter_user_id,invitee_user_id,invitee_mobile,invitation_type,status,pending_scope_key,expires_at) VALUES(?,?,?,?,?,'SECONDARY_BIND','PENDING','PENDING',?)",base+10+n,n==0?first:second,parent,other,"13800001111",now.plusDays(1));
        assertThat(invitations.countReceivedPending(other,null,now)).isEqualTo(2);
        assertThat(service.getReceivedInvitationsPage(other,1,1).total()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.getReceivedInvitationsPage(other,1,101)).isInstanceOf(IllegalArgumentException.class);
        assertThat(invitations.countReceivedPending(parent,null,now)).isZero();
        var invitationOne=invitations.findReceivedPendingPage(other,null,now,0,1);var invitationTwo=invitations.findReceivedPendingPage(other,null,now,1,1);
        assertThat(invitationOne).hasSize(1);assertThat(invitationTwo).hasSize(1);assertThat(invitationOne.get(0).invitationId()).isNotEqualTo(invitationTwo.get(0).invitationId());
        assertThat(invitations.countReceivedPending(parent,"13800001111",now)).isEqualTo(2);
        assertThat(invitations.countReceivedPending(other,null,now.plusDays(2))).isZero();
        jdbc.update("UPDATE sys_feature_toggle SET status='DISABLED' WHERE feature_code='PARENT_RELATIONSHIP_MANAGEMENT'");
        // JDBC fixture updates bypass MyBatis' transaction-local query cache.
        sqlSession.clearCache();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->service.getRelationshipStudentsPage(parent,1,20))
                .isInstanceOf(com.lingdong.learning.feature.application.FeatureDisabledException.class);
    }
}
