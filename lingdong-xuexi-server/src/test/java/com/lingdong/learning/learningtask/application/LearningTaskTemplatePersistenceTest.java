package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LearningTaskTemplatePersistenceTest {
    private static final Long PARENT_ID = 8900000000000000101L;

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LearningTaskTemplateService templateService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into sys_user (id, username, display_name, user_type, status)
                values (?, 'template_parent', '模板测试家长', 'PLATFORM', 'ENABLED')
                """, PARENT_ID);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("""
                delete from learn_task_template_tag
                where template_id in (
                    select id from learn_task_template where owner_user_id = ?
                )
                """, PARENT_ID);
        jdbcTemplate.update(
                "delete from learn_task_template where owner_user_id = ?", PARENT_ID);
        jdbcTemplate.update("delete from sys_user where id = ?", PARENT_ID);
    }

    @Test
    void persistsTagsVersionsLogicalDeleteNameReuseAndAtomicOrder() {
        AuthenticatedUser parent = new AuthenticatedUser(
                PARENT_ID, 8900000000000000102L, "template_parent", "模板测试家长",
                AuthClientType.WEB, List.of("PARENT"));
        LearningTaskTemplateView first = templateService.create(parent, input("阅读模板"));
        LearningTaskTemplateView second = templateService.create(parent, input("口算模板"));

        LearningTaskTemplateView updated = templateService.update(
                parent, first.id(), first.versionNo(), new LearningTaskTemplateInput(
                        "阅读模板", "亲子阅读", 2, 45,
                        "GENERAL", List.of("DAILY"), "更新后的备注"));
        List<LearningTaskTemplateView> reordered = templateService.reorder(parent, List.of(
                new LearningTaskTemplateOrderItem(second.id(), second.versionNo()),
                new LearningTaskTemplateOrderItem(updated.id(), updated.versionNo())));
        LearningTaskTemplateView reorderedSecond = personal(reordered).stream()
                .filter(template -> template.id().equals(second.id()))
                .findFirst().orElseThrow();
        templateService.delete(parent, second.id(), reorderedSecond.versionNo());
        LearningTaskTemplateView reused = templateService.create(parent, input("口算模板"));

        assertThat(first.id().toString()).hasSize(19);
        assertThat(updated.versionNo()).isEqualTo(2L);
        assertThat(updated.taskTitle()).isEqualTo("亲子阅读");
        assertThat(updated.tagCodes()).containsExactly("DAILY");
        assertThat(personal(reordered).stream()
                .sorted(Comparator.comparing(LearningTaskTemplateView::sortOrder))
                .map(LearningTaskTemplateView::id).toList())
                .containsExactly(second.id(), first.id());
        assertThat(reused.id()).isNotEqualTo(second.id());
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_template
                where owner_user_id = ? and template_name = '口算模板' and status = 'DELETED'
                """, Integer.class, PARENT_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select count(*) from learn_task_template
                where owner_user_id = ? and template_name = '口算模板' and status = 'ENABLED'
                """, Integer.class, PARENT_ID)).isEqualTo(1);
    }

    private List<LearningTaskTemplateView> personal(List<LearningTaskTemplateView> values) {
        return values.stream()
                .filter(template -> "PERSONAL".equals(template.templateScope()))
                .toList();
    }

    private LearningTaskTemplateInput input(String name) {
        return new LearningTaskTemplateInput(
                name, name, 1, 30, "GENERAL", List.of("DAILY"), null);
    }
}
