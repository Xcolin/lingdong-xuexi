package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.AnonymousRankPreferenceMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 模拟锁等待前旧快照与锁后当前读，MySQL 实网隔离级别仍需 R10 验证。 */
class AnonymousRankConcurrentWithdrawalTest {
    @Test void doesNotReportWithdrawnWhenConcurrentEnableCommittedAfterSnapshot() {
        var access = mock(AnonymousRankAccess.class);
        var preferences = mock(AnonymousRankPreferenceMapper.class);
        var users = mock(UserMapper.class);
        long parent=1874244142494698001L, student=parent+1, classroom=parent+2;
        var user = new AuthenticatedUser(parent,parent+9,"parent","家长",AuthClientType.WEB,List.of("PARENT"));
        var service = new AnonymousRankQueryService(access,preferences,mock(AnonymousRankMapper.class),users,mock(IdGenerator.class));
        when(preferences.find(parent,student,classroom)).thenReturn(null);
        when(preferences.findForUpdate(parent,student,classroom)).thenReturn(
                new AnonymousRankPreferenceMapper.Row(parent+3,parent,student,classroom,true,1));
        assertThatThrownBy(() -> service.set(user,student,classroom,false,0)).isInstanceOf(IllegalStateException.class);
        verify(preferences,never()).update(any(),anyLong());
    }
}
