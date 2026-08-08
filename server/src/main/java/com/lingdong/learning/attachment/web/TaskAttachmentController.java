package com.lingdong.learning.attachment.web;

import com.lingdong.learning.attachment.application.AttachmentContentView;
import com.lingdong.learning.attachment.application.TaskAttachmentApplicationService;
import com.lingdong.learning.attachment.application.UploadTaskAttachmentCommand;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 统一附件上传和受控内容访问入口。 */
@RestController
@RequestMapping("/api/v1/attachments")
public class TaskAttachmentController {
    private final TaskAttachmentApplicationService attachmentService;

    public TaskAttachmentController(TaskAttachmentApplicationService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @RequirePermission("ATTACHMENT_UPLOAD")
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskAttachmentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam String moduleCode,
            @RequestParam String fileCategory,
            @RequestParam MultipartFile file
    ) {
        try {
            return ResponseEntity.status(201).body(TaskAttachmentResponse.from(
                    attachmentService.upload(currentUser, new UploadTaskAttachmentCommand(
                            moduleCode, fileCategory, file.getOriginalFilename(),
                            file.getContentType(), file.getBytes()))));
        } catch (IOException exception) {
            throw new IllegalArgumentException("上传文件无法读取", exception);
        }
    }

    @RequirePermission("ATTACHMENT_READ")
    @GetMapping("/{id}")
    public TaskAttachmentResponse findMetadata(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        return TaskAttachmentResponse.from(attachmentService.findMetadata(currentUser, id));
    }

    @RequirePermission("ATTACHMENT_READ")
    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> readContent(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        AttachmentContentView content = attachmentService.readContent(currentUser, id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (IllegalArgumentException exception) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(content.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.content().length)
                .body(content.content());
    }

    @RequirePermission("ATTACHMENT_UPLOAD")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id
    ) {
        attachmentService.deleteUnattached(currentUser, id);
        return ResponseEntity.noContent().build();
    }
}
