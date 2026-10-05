package com.lingdong.learning.user.application;

import com.lingdong.learning.user.infrastructure.persistence.UserNameRow;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 校验申请人/审核人姓名解析的空值与缺失账号回退行为。 */
class UserDisplayNameResolverTest {

    @Test
    void resolvesNameAndFallsBackForMissingOrNullIdentifiers() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findNamesByIds(anyCollection()))
                .thenReturn(List.of(new UserNameRow(10L, "张三")));
        UserDisplayNameResolver resolver = new UserDisplayNameResolver(userMapper);

        assertThat(resolver.resolve(10L)).isEqualTo("张三");
        assertThat(resolver.resolve(99L)).isEqualTo("未知用户");
        assertThat(resolver.resolve(null)).isNull();
    }

    @Test
    void batchResolutionSkipsQueryWhenNoIdentifiers() {
        UserMapper userMapper = mock(UserMapper.class);
        UserDisplayNameResolver resolver = new UserDisplayNameResolver(userMapper);

        assertThat(resolver.resolveAll(List.of())).isEmpty();
        assertThat(resolver.resolveAll(null)).isEmpty();
        verify(userMapper, never()).findNamesByIds(anyCollection());
    }

    @Test
    void batchResolutionMapsIdentifiersToNames() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findNamesByIds(anyCollection()))
                .thenReturn(List.of(new UserNameRow(1L, "李四"), new UserNameRow(2L, "王五")));
        UserDisplayNameResolver resolver = new UserDisplayNameResolver(userMapper);

        var names = resolver.resolveAll(Arrays.asList(1L, 2L, 3L, null));
        assertThat(names).containsEntry(1L, "李四").containsEntry(2L, "王五");
        assertThat(resolver.nameOf(names, 1L)).isEqualTo("李四");
        assertThat(resolver.nameOf(names, 3L)).isEqualTo("未知用户");
        assertThat(resolver.nameOf(names, null)).isNull();
    }

    @Test
    void deduplicatesIdentifiersBeforeQuery() {
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.findNamesByIds(anyCollection()))
                .thenReturn(List.of(new UserNameRow(7L, "赵六")));
        UserDisplayNameResolver resolver = new UserDisplayNameResolver(userMapper);

        resolver.resolveAll(Arrays.asList(7L, 7L, null));
        verify(userMapper).findNamesByIds(org.mockito.ArgumentMatchers.argThat(
                (Collection<Long> ids) -> ids.size() == 1 && ids.contains(7L)));
    }
}
