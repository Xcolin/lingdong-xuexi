package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** 统一处理任务打卡图片的上传、业务关联、读取鉴权和删除。 */
@Service
public class TaskAttachmentApplicationService {
    public static final String MODULE_CODE = "LEARNING_TASK_CHECKIN";
    public static final String FILE_CATEGORY = "IMAGE";
    private static final String RELATION_TYPE = "IMAGE";
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final int MAX_FILE_COUNT = 9;

    private final AttachmentFileApplicationService fileService;
    private final ManagedFileMapper fileMapper;
    private final FileRelationMapper relationMapper;
    private final AttachmentContentStorage contentStorage;
    private final FeatureAccessService featureAccessService;

    public TaskAttachmentApplicationService(
            AttachmentFileApplicationService fileService,
            ManagedFileMapper fileMapper,
            FileRelationMapper relationMapper,
            AttachmentContentStorage contentStorage,
            FeatureAccessService featureAccessService
    ) {
        this.fileService = fileService;
        this.fileMapper = fileMapper;
        this.relationMapper = relationMapper;
        this.contentStorage = contentStorage;
        this.featureAccessService = featureAccessService;
    }

    @Transactional
    public TaskAttachmentView upload(AuthenticatedUser currentUser, UploadTaskAttachmentCommand command) {
        requireCurrentUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (command == null || command.content() == null || command.content().length == 0) {
            throw new IllegalArgumentException("上传图片不能为空");
        }
        if (!MODULE_CODE.equals(normalizeCode(command.moduleCode()))
                || !FILE_CATEGORY.equals(normalizeCode(command.fileCategory()))) {
            throw new IllegalArgumentException("附件业务范围不合法");
        }
        String detectedContentType = validateImageContent(
                command.originalName(), command.contentType(), command.content());
        ManagedFile registered = fileService.registerUpload(new RegisterAttachmentFileCommand(
                currentUser.userId(), MODULE_CODE, FILE_CATEGORY, command.originalName(),
                detectedContentType, command.content().length));
        try {
            contentStorage.store(registered.storageKey(), command.content());
            ManagedFile completed = fileService.completeUpload(new CompleteAttachmentUploadCommand(
                    registered.id(), command.content().length, detectedContentType, sha256(command.content())));
            return toView(completed);
        } catch (RuntimeException exception) {
            contentStorage.delete(registered.storageKey());
            throw exception;
        }
    }

    @Transactional
    public void attachToCheckIn(Long uploaderId, List<Long> fileIds, Long checkInId) {
        List<Long> normalizedIds = normalizeFileIds(fileIds);
        for (Long fileId : normalizedIds) {
            ManagedFileRecord file = requireFile(fileId);
            if (!uploaderId.equals(file.uploaderId()) || file.status() != FileStatus.AVAILABLE
                    || !MODULE_CODE.equals(file.moduleCode()) || !FILE_CATEGORY.equals(file.fileCategory())) {
                throw new ResourceNotFoundException("附件不存在或不可关联");
            }
            if (relationMapper.countActiveByFileId(fileId) > 0) {
                throw new IllegalStateException("附件已关联业务，不能重复使用");
            }
            fileService.attachToBusiness(new AttachFileToBusinessCommand(
                    fileId, MODULE_CODE, checkInId, RELATION_TYPE, "BUSINESS_AUTHORIZED"));
        }
    }

    @Transactional(readOnly = true)
    public TaskAttachmentView findMetadata(AuthenticatedUser currentUser, Long fileId) {
        return toView(requireReadable(currentUser, fileId));
    }

    @Transactional(readOnly = true)
    public AttachmentContentView readContent(AuthenticatedUser currentUser, Long fileId) {
        ManagedFileRecord file = requireReadable(currentUser, fileId);
        return new AttachmentContentView(
                file.originalName(), file.contentType(), contentStorage.read(file.storageKey()));
    }

    @Transactional
    public void deleteUnattached(AuthenticatedUser currentUser, Long fileId) {
        requireCurrentUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        ManagedFileRecord file = requireFile(fileId);
        if (!currentUser.userId().equals(file.uploaderId())) {
            throw new ResourceNotFoundException("附件不存在或不可删除");
        }
        if (file.status() == FileStatus.RETIRED) {
            return;
        }
        if (file.status() != FileStatus.AVAILABLE || relationMapper.countActiveByFileId(file.id()) > 0) {
            throw new IllegalStateException("附件当前不能删除");
        }
        if (fileMapper.markRetired(file.id()) != 1) {
            throw new IllegalStateException("附件状态已变化");
        }
        contentStorage.delete(file.storageKey());
    }

    @Transactional(readOnly = true)
    public List<TaskAttachmentView> findByCheckInId(Long checkInId) {
        if (checkInId == null) {
            return List.of();
        }
        return relationMapper.findActiveFilesByBusiness(MODULE_CODE, checkInId, RELATION_TYPE)
                .stream().map(this::toView).toList();
    }

    private ManagedFileRecord requireReadable(AuthenticatedUser currentUser, Long fileId) {
        requireCurrentUser(currentUser);
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        ManagedFileRecord file = requireFile(fileId);
        boolean uploader = currentUser.userId().equals(file.uploaderId());
        boolean reviewer = relationMapper.countReadableByCurrentReviewer(file.id(), currentUser.userId()) > 0;
        if (file.status() != FileStatus.AVAILABLE || (!uploader && !reviewer)) {
            throw new ResourceNotFoundException("附件不存在或不可访问");
        }
        return file;
    }

    private ManagedFileRecord requireFile(Long fileId) {
        ManagedFileRecord file = fileId == null ? null : fileMapper.findById(fileId);
        if (file == null) {
            throw new ResourceNotFoundException("附件不存在或不可访问");
        }
        return file;
    }

    private List<Long> normalizeFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long fileId : fileIds) {
            if (fileId == null || fileId <= 0 || !distinct.add(fileId)) {
                throw new IllegalArgumentException("附件标识不合法或重复");
            }
        }
        if (distinct.size() > MAX_FILE_COUNT) {
            throw new IllegalArgumentException("一次打卡最多上传9张图片");
        }
        return List.copyOf(distinct);
    }

    private String validateImageContent(String originalName, String contentType, byte[] content) {
        String extension = extensionOf(originalName);
        String normalizedContentType = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        boolean jpeg = content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
        boolean png = content.length >= 8 && (content[0] & 0xff) == 0x89
                && content[1] == 0x50 && content[2] == 0x4e && content[3] == 0x47
                && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a;
        boolean unspecifiedType = normalizedContentType.isEmpty()
                || "application/octet-stream".equals(normalizedContentType);
        boolean validJpeg = jpeg && ("jpg".equals(extension) || "jpeg".equals(extension))
                && (unspecifiedType || "image/jpeg".equals(normalizedContentType));
        boolean validPng = png && "png".equals(extension)
                && (unspecifiedType || "image/png".equals(normalizedContentType));
        if (!validJpeg && !validPng) {
            throw new IllegalArgumentException("图片扩展名、内容类型与真实格式不一致");
        }
        return validJpeg ? "image/jpeg" : "image/png";
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int index = originalName.lastIndexOf('.');
        return index < 0 ? "" : originalName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持SHA-256", exception);
        }
    }

    private TaskAttachmentView toView(ManagedFile file) {
        return new TaskAttachmentView(file.id(), file.originalName(), file.contentType(),
                file.sizeBytes(), "/api/v1/attachments/" + file.id() + "/content");
    }

    private TaskAttachmentView toView(ManagedFileRecord file) {
        return new TaskAttachmentView(file.id(), file.originalName(), file.contentType(),
                file.sizeBytes(), "/api/v1/attachments/" + file.id() + "/content");
    }

    private void requireCurrentUser(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.userId() == null) {
            throw new ResourceNotFoundException("附件不存在或不可访问");
        }
    }
}
