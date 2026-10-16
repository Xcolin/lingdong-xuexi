package com.lingdong.learning.audit.web;
import com.lingdong.learning.audit.application.SystemTask;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class SystemTaskActorIdentityTest {
    @Test void responsePreservesActorIdentifiersSeparatelyFromDisplayNames() {
        var task=mock(SystemTask.class);when(task.id()).thenReturn(1L);when(task.submittedBy()).thenReturn(2L);when(task.reviewedBy()).thenReturn(3L);
        var response=SystemTaskQueryController.Response.from(task,id->"显示姓名");
        assertThat(response.submittedBy()).isEqualTo("显示姓名");
        assertThat(response.submittedById()).isEqualTo("2");
        assertThat(response.reviewedById()).isEqualTo("3");
    }
}
