package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateTagRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningTaskTemplateServiceTest {
    private final FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
    private final DictionaryQueryService dictionaryQueryService = mock(DictionaryQueryService.class);
    private final LearningTaskTemplateMapper templateMapper = mock(LearningTaskTemplateMapper.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final LearningTaskTemplateService service = new LearningTaskTemplateService(
            featureAccessService, dictionaryQueryService, templateMapper, idGenerator);
    private final AuthenticatedUser parent = new AuthenticatedUser(
            44L, 55L, "parent", "家长", AuthClientType.WEB, List.of("PARENT"));

    @BeforeEach
    void setUp() {
        when(dictionaryQueryService.findEnabledItems("TASK_CATEGORY"))
                .thenReturn(List.of(dictionaryItem("GENERAL")));
        when(dictionaryQueryService.findEnabledItems("TASK_TAG"))
                .thenReturn(List.of(dictionaryItem("DAILY")));
    }

    @Test
    void listsSystemTemplatesAndOnlyCurrentParentsPersonalTemplates() {
        when(templateMapper.findVisible(44L)).thenReturn(List.of(
                row(101L, "SYSTEM", null, "每日阅读30分钟", 10, 1L),
                row(102L, "PERSONAL", 44L, "周末阅读", 20, 3L)));
        when(templateMapper.findTagsByTemplateIds(List.of(101L, 102L))).thenReturn(List.of(
                new LearningTaskTemplateTagRow(101L, "DAILY"),
                new LearningTaskTemplateTagRow(102L, "DAILY")));

        List<LearningTaskTemplateView> result = service.list(parent);

        assertThat(result).extracting(LearningTaskTemplateView::templateName)
                .containsExactly("每日阅读30分钟", "周末阅读");
        assertThat(result).allSatisfy(template -> assertThat(template.tagCodes())
                .containsExactly("DAILY"));
        verify(templateMapper).findVisible(44L);
    }

    @Test
    void rejectsNonWebParentBeforeReadingTemplates() {
        AuthenticatedUser teacher = new AuthenticatedUser(
                77L, 88L, "teacher", "教师", AuthClientType.WEB, List.of("TEACHER"));

        assertThatThrownBy(() -> service.list(teacher))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仅 Web 家长可使用任务模板");

        verify(templateMapper, never()).findVisible(77L);
    }

    @Test
    void createsNormalizedPersonalTemplateWithServerOwnedScopeAndTags() {
        when(templateMapper.countEnabledPersonal(44L)).thenReturn(2);
        when(templateMapper.existsEnabledName("44", "晚间阅读")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(9001L, 9002L);
        when(templateMapper.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(templateMapper.insertTag(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);
        when(templateMapper.findById(9001L)).thenReturn(
                row(9001L, "PERSONAL", 44L, "晚间阅读", 30, 1L));
        when(templateMapper.findTagsByTemplateIds(List.of(9001L))).thenReturn(List.of(
                new LearningTaskTemplateTagRow(9001L, "DAILY")));

        LearningTaskTemplateView created = service.create(parent, new LearningTaskTemplateInput(
                " 晚间阅读 ", " 阅读 30 分钟 ", 1, 30,
                "general", List.of("daily", "DAILY"), " 亲子共读 "));

        assertThat(created.id()).isEqualTo(9001L);
        assertThat(created.templateScope()).isEqualTo("PERSONAL");
        assertThat(created.templateName()).isEqualTo("晚间阅读");
        verify(templateMapper).insert(org.mockito.ArgumentMatchers.argThat(template ->
                template.ownerUserId().equals(44L)
                        && template.ownerScopeKey().equals("44")
                        && template.activeNameKey().equals("晚间阅读")
                        && template.taskTitle().equals("阅读 30 分钟")
                        && template.categoryCode().equals("GENERAL")));
    }

    @Test
    void rejectsEditingSystemTemplateAsUnavailableResource() {
        when(templateMapper.findByIdForUpdate(101L)).thenReturn(
                row(101L, "SYSTEM", null, "每日阅读30分钟", 10, 1L));

        assertThatThrownBy(() -> service.update(parent, 101L, 1L, input("晚间阅读")))
                .isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class)
                .hasMessage("个人任务模板不存在或不可管理");

        verify(templateMapper, never()).update(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsReorderingWhenRequestDoesNotContainEveryActivePersonalTemplate() {
        when(templateMapper.findEnabledPersonalForUpdate(44L)).thenReturn(List.of(
                row(201L, "PERSONAL", 44L, "模板一", 10, 2L),
                row(202L, "PERSONAL", 44L, "模板二", 20, 4L)));

        assertThatThrownBy(() -> service.reorder(parent, List.of(
                new LearningTaskTemplateOrderItem(201L, 2L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("个人模板列表已变化，请刷新后重试");

        verify(templateMapper, never()).updateSort(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void mapsConcurrentCreateNameConflictToBusinessConflict() {
        when(templateMapper.countEnabledPersonal(44L)).thenReturn(2);
        when(templateMapper.existsEnabledName("44", "晚间阅读")).thenReturn(false);
        when(idGenerator.nextId()).thenReturn(9001L);
        when(templateMapper.insert(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DuplicateKeyException("uk_task_template_owner_name"));

        assertThatThrownBy(() -> service.create(parent, input("晚间阅读")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("个人任务模板名称已存在");
    }

    @Test
    void mapsConcurrentUpdateNameConflictToBusinessConflict() {
        when(templateMapper.findByIdForUpdate(201L)).thenReturn(
                row(201L, "PERSONAL", 44L, "旧模板", 10, 2L));
        when(templateMapper.existsOtherEnabledName("44", "晚间阅读", 201L)).thenReturn(false);
        when(templateMapper.update(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(2L)))
                .thenThrow(new DuplicateKeyException("uk_task_template_owner_name"));

        assertThatThrownBy(() -> service.update(parent, 201L, 2L, input("晚间阅读")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("个人任务模板名称已存在");
    }

    private LearningTaskTemplateInput input(String name) {
        return new LearningTaskTemplateInput(
                name, "阅读 30 分钟", 1, 30, "GENERAL", List.of("DAILY"), null);
    }

    private LearningTaskTemplateRow row(
            Long id, String scope, Long ownerId, String name, int sortOrder, long version
    ) {
        return new LearningTaskTemplateRow(
                id, scope, ownerId, scope.equals("SYSTEM") ? "SYSTEM" : ownerId.toString(),
                name, name, name, 1, 30, "GENERAL", null, sortOrder,
                "ENABLED", version, ownerId, ownerId, null, null);
    }

    private DictionaryItem dictionaryItem(String code) {
        return DictionaryItem.enabled(1L, 1L, code, code, 10, false);
    }
}
