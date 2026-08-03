package com.lingdong.learning.cache.application;

import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.audit.application.SystemTaskType;
import com.lingdong.learning.audit.infrastructure.persistence.SystemTaskMapper;
import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperation;
import com.lingdong.learning.cache.domain.CacheOperationStatus;
import com.lingdong.learning.cache.domain.CacheOperationType;
import com.lingdong.learning.cache.infrastructure.persistence.CacheOperationMapper;
import com.lingdong.learning.dictionary.application.CreateDictionaryItemCommand;
import com.lingdong.learning.dictionary.application.CreateDictionaryTypeCommand;
import com.lingdong.learning.dictionary.application.DictionaryApplicationService;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.domain.DictionaryType;
import com.lingdong.learning.dictionary.infrastructure.cache.DictionaryItemCache;
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
class CacheOperationApplicationServiceTest {
    @Autowired
    private CacheOperationApplicationService cacheOperationApplicationService;

    @Autowired
    private CacheOperationMapper cacheOperationMapper;

    @Autowired
    private DictionaryApplicationService dictionaryApplicationService;

    @Autowired
    private DictionaryQueryService dictionaryQueryService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private SystemTaskMapper systemTaskMapper;

    @Autowired
    private UserAccessApplicationService userAccessApplicationService;

    @Autowired
    private RoleMapper roleMapper;

    @Test
    void refreshesDictionaryCacheAndRecordsSucceededOperation() {
        User administrator = createUserWithRole("cache_dictionary_admin", "缓存管理员", "SYS_ADMIN");
        DictionaryType type = createDictionaryTypeWithItem(
                administrator, "TASK_SOURCE", "任务来源", "FAMILY", "家庭"
        );
        dictionaryQueryService.findEnabledItems(type.code());

        CacheOperation operation = cacheOperationApplicationService.execute(
                new ExecuteCacheOperationCommand(
                        administrator.id(), CacheDomain.DICTIONARY, CacheOperationType.REFRESH, "刷新任务来源字典缓存"
                )
        );

        Cache cache = cacheManager.getCache(DictionaryItemCache.CACHE_NAME);
        assertThat(Long.toString(operation.id())).hasSize(19);
        assertThat(operation.status()).isEqualTo(CacheOperationStatus.SUCCEEDED);
        assertThat(cache).isNotNull();
        assertThat(cache.get(type.code())).isNotNull();
    }

    @Test
    void requiresSystemTaskBeforeClearingAllCaches() {
        User administrator = createUserWithRole("cache_global_admin", "全量缓存管理员", "SYS_ADMIN");
        User auditor = createUserWithRole("cache_global_auditor", "全量缓存审核员", "SYS_AUDITOR");

        assertThatThrownBy(() -> cacheOperationApplicationService.execute(
                new ExecuteCacheOperationCommand(administrator.id(), CacheDomain.ALL, CacheOperationType.CLEAR, "全量清除缓存")
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统任务");

        CacheOperation draft = cacheOperationApplicationService.createHighRiskDraft(
                new CreateHighRiskCacheOperationCommand(
                        administrator.id(),
                        CacheDomain.ALL,
                        CacheOperationType.CLEAR,
                        "全量清除缓存",
                        "发布后清除全部已注册缓存",
                        true
                )
        );
        assertThat(draft.taskId()).isNotNull();
        assertThat(systemTaskMapper.findById(draft.taskId()).type()).isEqualTo(SystemTaskType.CACHE_CLEAR);
        cacheOperationApplicationService.submit(draft.taskId(), administrator.id());
        CacheOperation executed = cacheOperationApplicationService.approveAndExecute(
                draft.taskId(), auditor.id(), "同意清理"
        );

        assertThat(executed.status()).isEqualTo(CacheOperationStatus.SUCCEEDED);
        assertThat(systemTaskMapper.findById(draft.taskId()).status()).isEqualTo(SystemTaskStatus.EFFECTIVE);
    }

    @Test
    void recordsFailureWhenNoHandlerExistsForRequestedCacheDomain() {
        User administrator = createUserWithRole("cache_permission_admin", "权限缓存管理员", "SYS_ADMIN");

        CacheOperation operation = cacheOperationApplicationService.execute(
                new ExecuteCacheOperationCommand(
                        administrator.id(), CacheDomain.PERMISSION, CacheOperationType.CLEAR, "清除权限缓存"
                )
        );

        assertThat(operation.status()).isEqualTo(CacheOperationStatus.FAILED);
        assertThat(operation.failureMessage()).contains("未注册");
        assertThat(cacheOperationMapper.findById(operation.id()).status()).isEqualTo(CacheOperationStatus.FAILED);
    }

    private DictionaryType createDictionaryTypeWithItem(
            User administrator,
            String typeCode,
            String typeName,
            String itemCode,
            String itemName
    ) {
        DictionaryType type = dictionaryApplicationService.createType(
                new CreateDictionaryTypeCommand(administrator.id(), typeCode, typeName, 10)
        );
        dictionaryApplicationService.createItem(
                new CreateDictionaryItemCommand(administrator.id(), type.id(), itemCode, itemName, 10, true)
        );
        return type;
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
