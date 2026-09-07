package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.TaskAttachmentApplicationService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskAttachmentControllerTest {
    @Test
    void downloadsAuthorizedContentAsAttachment() {
        TaskAttachmentApplicationService service = mock(TaskAttachmentApplicationService.class);
        TaskAttachmentController controller = new TaskAttachmentController(service);
        AuthenticatedUser user = new AuthenticatedUser(
                1874244142494647001L, 1874244142494647002L,
                "reviewer", "审核人", AuthClientType.WEB, List.of("TEACHER"));
        when(service.readContent(user, 1874244142494647003L)).thenReturn(
                new AttachmentContentView("学习凭证.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        ResponseEntity<byte[]> response = controller.download(user, 1874244142494647003L);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment;");
        assertThat(response.getBody()).containsExactly(1, 2, 3);
    }
}
