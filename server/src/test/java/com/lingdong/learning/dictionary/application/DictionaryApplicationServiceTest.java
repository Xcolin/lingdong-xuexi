package com.lingdong.learning.dictionary.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.domain.DictionaryStatus;
import com.lingdong.learning.dictionary.domain.DictionaryType;
import com.lingdong.learning.dictionary.infrastructure.cache.DictionaryItemCache;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryTypeMapper;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DictionaryApplicationServiceTest {
    @Autowired
    private DictionaryApplicationService dictionaryApplicationService;

    @Autowired
    private DictionaryItemMapper dictionaryItemMapper;

    @Autowired
    private DictionaryTypeMapper dictionaryTypeMapper;

    @Autowired
    private DictionaryQueryService dictionaryQueryService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private IdGenerator idGenerator;

    @Test
    void letsSystemAdministratorCreateTypeAndKeepsOnlyLatestDefaultItem() {
        User administrator = createUserWithRole("dictionary_admin", "字典管理员", "SYS_ADMIN");

        DictionaryType type = dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), "LEARNING_CONTENT_TEST", "学习内容测试分类", 10)
        );
        DictionaryItem first = dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "READING", "阅读", 10, true)
        );
        DictionaryItem second = dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "MATH", "数学", 20, true)
        );

        assertThat(Long.toString(type.id())).hasSize(19);
        assertThat(Long.toString(first.id())).hasSize(19);
        assertThat(Long.toString(second.id())).hasSize(19);
        assertThat(dictionaryItemMapper.findById(first.id()).defaultItem()).isFalse();
        assertThat(dictionaryItemMapper.findById(second.id()).defaultItem()).isTrue();
    }

    @Test
    void rejectsDirectCreationOfKeyDictionaryType() {
        User administrator = createUserWithRole("key_dictionary_admin", "关键字典管理员", "SYS_ADMIN");

        assertThatThrownBy(() -> dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), "TASK_STATUS", "任务状态", 10)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统任务审批");
    }

    @Test
    void disablesItemAndEvictsCachedSelectableItems() {
        User administrator = createUserWithRole("dictionary_cache_admin", "字典缓存管理员", "SYS_ADMIN");
        DictionaryType type = dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), "TASK_PRIORITY", "任务优先级", 10)
        );
        DictionaryItem item = dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "URGENT", "紧急", 10, true)
        );

        assertThat(dictionaryQueryService.findEnabledItems("task_priority"))
                .extracting(DictionaryItem::code)
                .containsExactly("URGENT");
        Cache cache = cacheManager.getCache(DictionaryItemCache.CACHE_NAME);
        assertThat(cache).isNotNull();
        assertThat(cache.get("TASK_PRIORITY")).isNotNull();

        dictionaryApplicationService.updateItem(new UpdateDictionaryItemCommand(
                administrator.id(), item.id(), "紧急", 10, false, DictionaryStatus.DISABLED
        ));

        assertThat(dictionaryItemMapper.findById(item.id()).defaultItem()).isFalse();
        assertThat(cache.get("TASK_PRIORITY")).isNull();
        assertThat(dictionaryQueryService.findEnabledItems("TASK_PRIORITY")).isEmpty();
    }

    @Test
    void disablingTypeHidesItsCachedItemsAndBlocksNewItems() {
        User administrator = createUserWithRole("dictionary_type_admin", "字典类型管理员", "SYS_ADMIN");
        DictionaryType type = dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), "COURSE_LEVEL", "课程难度", 10)
        );
        dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "BASIC", "基础", 10, true)
        );
        assertThat(dictionaryQueryService.findEnabledItems("COURSE_LEVEL")).hasSize(1);

        DictionaryType disabled = dictionaryApplicationService.updateType(new UpdateDictionaryTypeCommand(
                administrator.id(), type.id(), "课程难度", 10, DictionaryStatus.DISABLED
        ));

        assertThat(disabled.status()).isEqualTo(DictionaryStatus.DISABLED);
        Cache cache = cacheManager.getCache(DictionaryItemCache.CACHE_NAME);
        assertThat(cache).isNotNull();
        assertThat(cache.get("COURSE_LEVEL")).isNull();
        assertThat(dictionaryQueryService.findEnabledItems("COURSE_LEVEL")).isEmpty();
        assertThatThrownBy(() -> dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "ADVANCED", "进阶", 20, false)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void rejectsDictionaryManagementByNonSystemAdministrator() {
        User organizationAdministrator = createUserWithRole("dictionary_org_admin", "机构管理员", "ORG_ADMIN");

        assertThatThrownBy(() -> dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(organizationAdministrator.id(), "CONTENT_SOURCE", "内容来源", 10)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅系统管理员");
    }

    @Test
    void rejectsDirectItemCreationForExistingKeyDictionaryType() {
        User administrator = createUserWithRole("key_dictionary_item_admin", "关键字典项管理员", "SYS_ADMIN");
        dictionaryTypeMapper.insert(DictionaryType.enabled(idGenerator.nextId(), "ROLE_TYPE", "角色类型", 10));
        DictionaryType keyType = dictionaryTypeMapper.findByCode("ROLE_TYPE");

        assertThatThrownBy(() -> dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), keyType.id(), "CUSTOM", "自定义", 10, false)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统任务审批");
    }

    @Test
    void rejectsDisabledItemAsDefault() {
        User administrator = createUserWithRole("disabled_default_admin", "停用默认项管理员", "SYS_ADMIN");
        DictionaryType type = dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), "EXAM_LEVEL", "考试等级", 10)
        );
        DictionaryItem item = dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), "LEVEL_ONE", "一级", 10, true)
        );

        assertThatThrownBy(() -> dictionaryApplicationService.updateItem(new UpdateDictionaryItemCommand(
                administrator.id(), item.id(), "一级", 10, true, DictionaryStatus.DISABLED
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能设为默认项");
        assertThat(dictionaryItemMapper.findById(item.id()).defaultItem()).isTrue();
    }

    private User createUserWithRole(String username, String displayName, String roleCode) {
        User user = userAccessApplicationService.createUser(
                new CreateUserCommand(username, displayName, null, UserType.PLATFORM)
        );
        Role role = roleMapper.findByCode(roleCode);
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(user.id(), role.id(), null));
        return user;
    }
}
