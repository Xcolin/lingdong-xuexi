package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTemplateTagRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 管理系统只读模板和当前家长的个人任务模板。 */
@Service
public class LearningTaskTemplateService {
    private static final int MAX_PERSONAL_TEMPLATES = 100;
    private static final int MAX_TAGS = 20;

    private final FeatureAccessService featureAccessService;
    private final DictionaryQueryService dictionaryQueryService;
    private final LearningTaskTemplateMapper templateMapper;
    private final IdGenerator idGenerator;

    public LearningTaskTemplateService(
            FeatureAccessService featureAccessService,
            DictionaryQueryService dictionaryQueryService,
            LearningTaskTemplateMapper templateMapper,
            IdGenerator idGenerator
    ) {
        this.featureAccessService = featureAccessService;
        this.dictionaryQueryService = dictionaryQueryService;
        this.templateMapper = templateMapper;
        this.idGenerator = idGenerator;
    }

    public List<LearningTaskTemplateView> list(AuthenticatedUser currentUser) {
        Long ownerUserId = authorize(currentUser);
        List<LearningTaskTemplateRow> rows = templateMapper.findVisible(ownerUserId);
        return toViews(rows);
    }

    @Transactional
    public LearningTaskTemplateView create(
            AuthenticatedUser currentUser, LearningTaskTemplateInput input
    ) {
        Long ownerUserId = authorize(currentUser);
        ValidatedTemplate validated = validate(input);
        if (templateMapper.countEnabledPersonal(ownerUserId) >= MAX_PERSONAL_TEMPLATES) {
            throw new IllegalStateException("个人任务模板不能超过 100 个");
        }
        String ownerScopeKey = ownerUserId.toString();
        if (templateMapper.existsEnabledName(ownerScopeKey, validated.templateName())) {
            throw new IllegalStateException("个人任务模板名称已存在");
        }
        Long templateId = idGenerator.nextId();
        LearningTaskTemplateRow row = new LearningTaskTemplateRow(
                templateId, "PERSONAL", ownerUserId, ownerScopeKey,
                validated.templateName(), validated.templateName(), validated.taskTitle(),
                validated.difficultyLevel(), validated.durationMinutes(),
                validated.categoryCode(), validated.remark(),
                templateMapper.findNextPersonalSortOrder(ownerUserId), "ENABLED", 1L,
                ownerUserId, ownerUserId, null, null);
        try {
            requireAffected(templateMapper.insert(row), "个人任务模板创建失败");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("个人任务模板名称已存在", exception);
        }
        replaceTags(templateId, validated.tagCodes(), false);
        return findView(templateId);
    }

    @Transactional
    public LearningTaskTemplateView update(
            AuthenticatedUser currentUser,
            Long templateId,
            Long expectedVersion,
            LearningTaskTemplateInput input
    ) {
        Long ownerUserId = authorize(currentUser);
        requirePositive(templateId, "任务模板标识不合法");
        requirePositive(expectedVersion, "任务模板版本不合法");
        LearningTaskTemplateRow existing = requireManagedPersonal(
                templateMapper.findByIdForUpdate(templateId), ownerUserId);
        if (!existing.versionNo().equals(expectedVersion)) {
            throw new IllegalStateException("个人任务模板已变化，请刷新后重试");
        }
        ValidatedTemplate validated = validate(input);
        String ownerScopeKey = ownerUserId.toString();
        if (templateMapper.existsOtherEnabledName(
                ownerScopeKey, validated.templateName(), templateId)) {
            throw new IllegalStateException("个人任务模板名称已存在");
        }
        LearningTaskTemplateRow updated = new LearningTaskTemplateRow(
                existing.id(), existing.templateScope(), existing.ownerUserId(), ownerScopeKey,
                validated.templateName(), validated.templateName(), validated.taskTitle(),
                validated.difficultyLevel(), validated.durationMinutes(),
                validated.categoryCode(), validated.remark(), existing.sortOrder(),
                existing.status(), existing.versionNo(), existing.createdByUserId(),
                ownerUserId, existing.createdAt(), existing.updatedAt());
        try {
            requireAffected(templateMapper.update(updated, expectedVersion),
                    "个人任务模板已变化，请刷新后重试");
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("个人任务模板名称已存在", exception);
        }
        replaceTags(templateId, validated.tagCodes(), true);
        return findView(templateId);
    }

    @Transactional
    public void delete(
            AuthenticatedUser currentUser, Long templateId, Long expectedVersion
    ) {
        Long ownerUserId = authorize(currentUser);
        requirePositive(templateId, "任务模板标识不合法");
        requirePositive(expectedVersion, "任务模板版本不合法");
        LearningTaskTemplateRow existing = requireManagedPersonal(
                templateMapper.findByIdForUpdate(templateId), ownerUserId);
        if (!existing.versionNo().equals(expectedVersion)
                || templateMapper.markDeleted(templateId, ownerUserId, expectedVersion) != 1) {
            throw new IllegalStateException("个人任务模板已变化，请刷新后重试");
        }
    }

    @Transactional
    public List<LearningTaskTemplateView> reorder(
            AuthenticatedUser currentUser,
            List<LearningTaskTemplateOrderItem> items
    ) {
        Long ownerUserId = authorize(currentUser);
        List<LearningTaskTemplateOrderItem> requested = items == null ? List.of() : List.copyOf(items);
        if (requested.size() > MAX_PERSONAL_TEMPLATES
                || requested.stream().anyMatch(item -> item == null
                || item.templateId() == null || item.templateId() <= 0
                || item.versionNo() == null || item.versionNo() <= 0)) {
            throw new IllegalArgumentException("个人模板排序参数不合法");
        }
        Set<Long> requestedIds = requested.stream()
                .map(LearningTaskTemplateOrderItem::templateId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<LearningTaskTemplateRow> current =
                templateMapper.findEnabledPersonalForUpdate(ownerUserId);
        Map<Long, LearningTaskTemplateRow> currentById = current.stream()
                .collect(Collectors.toMap(LearningTaskTemplateRow::id, Function.identity()));
        if (requestedIds.size() != requested.size()
                || currentById.size() != requested.size()
                || !currentById.keySet().equals(requestedIds)) {
            throw new IllegalStateException("个人模板列表已变化，请刷新后重试");
        }
        for (int index = 0; index < requested.size(); index++) {
            LearningTaskTemplateOrderItem item = requested.get(index);
            LearningTaskTemplateRow row = currentById.get(item.templateId());
            if (!row.versionNo().equals(item.versionNo())
                    || templateMapper.updateSort(
                    item.templateId(), (index + 1) * 10, ownerUserId, item.versionNo()) != 1) {
                throw new IllegalStateException("个人模板列表已变化，请刷新后重试");
            }
        }
        return list(currentUser);
    }

    private Long authorize(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled("LEARNING_TASK_MANAGEMENT", null);
        featureAccessService.requireEnabled("LEARNING_TASK_TEMPLATE", null);
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new IllegalArgumentException("仅 Web 家长可使用任务模板");
        }
        return currentUser.userId();
    }

    private LearningTaskTemplateRow requireManagedPersonal(
            LearningTaskTemplateRow row, Long ownerUserId
    ) {
        if (row == null || !"PERSONAL".equals(row.templateScope())
                || !ownerUserId.equals(row.ownerUserId()) || !"ENABLED".equals(row.status())) {
            throw new ResourceNotFoundException("个人任务模板不存在或不可管理");
        }
        return row;
    }

    private ValidatedTemplate validate(LearningTaskTemplateInput input) {
        if (input == null) {
            throw new IllegalArgumentException("任务模板不能为空");
        }
        String templateName = requiredText(input.templateName(), "模板名称", 50);
        String taskTitle = requiredText(input.taskTitle(), "任务标题", 50);
        int difficulty = requireRange(input.difficultyLevel(), "任务难度", 1, 3);
        int duration = requireRange(input.durationMinutes(), "执行时长", 1, 1440);
        String categoryCode = optionalCode(input.categoryCode());
        if (categoryCode != null && !enabledCodes("TASK_CATEGORY").contains(categoryCode)) {
            throw new IllegalArgumentException("任务分类不存在或已停用");
        }
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (input.tagCodes() != null) {
            for (String value : input.tagCodes()) {
                String code = optionalCode(value);
                if (code == null) {
                    throw new IllegalArgumentException("任务标签编码不能为空");
                }
                tags.add(code);
            }
        }
        if (tags.size() > MAX_TAGS || !enabledCodes("TASK_TAG").containsAll(tags)) {
            throw new IllegalArgumentException("任务标签不存在、已停用或超过 20 个");
        }
        return new ValidatedTemplate(
                templateName, taskTitle, difficulty, duration, categoryCode,
                List.copyOf(tags), optionalText(input.remark(), "任务备注", 200));
    }

    private Set<String> enabledCodes(String typeCode) {
        return dictionaryQueryService.findEnabledItems(typeCode).stream()
                .map(DictionaryItem::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void replaceTags(Long templateId, List<String> tagCodes, boolean deleteExisting) {
        if (deleteExisting) {
            templateMapper.deleteTags(templateId);
        }
        for (String tagCode : tagCodes) {
            requireAffected(templateMapper.insertTag(idGenerator.nextId(), templateId, tagCode),
                    "任务模板标签保存失败");
        }
    }

    private LearningTaskTemplateView findView(Long templateId) {
        LearningTaskTemplateRow row = templateMapper.findById(templateId);
        if (row == null) {
            throw new ResourceNotFoundException("任务模板不存在");
        }
        return toViews(List.of(row)).get(0);
    }

    private List<LearningTaskTemplateView> toViews(List<LearningTaskTemplateRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> tagsByTemplate = templateMapper.findTagsByTemplateIds(
                        rows.stream().map(LearningTaskTemplateRow::id).toList()).stream()
                .collect(Collectors.groupingBy(
                        LearningTaskTemplateTagRow::templateId,
                        Collectors.mapping(LearningTaskTemplateTagRow::tagCode, Collectors.toList())));
        List<LearningTaskTemplateView> result = new ArrayList<>(rows.size());
        for (LearningTaskTemplateRow row : rows) {
            result.add(new LearningTaskTemplateView(
                    row.id(), row.templateScope(), row.templateName(), row.taskTitle(),
                    row.difficultyLevel(), row.durationMinutes(), row.categoryCode(),
                    List.copyOf(tagsByTemplate.getOrDefault(row.id(), List.of())), row.remark(),
                    row.sortOrder(), row.versionNo(), row.createdAt(), row.updatedAt()));
        }
        return List.copyOf(result);
    }

    private int requireRange(Integer value, String fieldName, int minimum, int maximum) {
        if (value == null || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + "必须在 " + minimum + " 至 " + maximum + " 之间");
        }
        return value;
    }

    private String requiredText(String value, String fieldName, int maximum) {
        String normalized = optionalText(value, fieldName, maximum);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, String fieldName, int maximum) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maximum + " 个字符");
        }
        return normalized;
    }

    private String optionalCode(String value) {
        String normalized = optionalText(value, "字典编码", 64);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireAffected(int affected, String message) {
        if (affected != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record ValidatedTemplate(
            String templateName,
            String taskTitle,
            int difficultyLevel,
            int durationMinutes,
            String categoryCode,
            List<String> tagCodes,
            String remark
    ) {
    }
}
