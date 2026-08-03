package com.lingdong.learning.attachment.application;

import com.lingdong.learning.attachment.domain.FileRelationRecord;
import com.lingdong.learning.attachment.domain.FileRelationStatus;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.domain.ManagedFileRecord;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Manages attachment metadata and visibility relations without coupling to a storage supplier SDK. */
@Service
public class AttachmentFileApplicationService {
    private final AttachmentRuleApplicationService ruleService;
    private final ManagedFileMapper fileMapper;
    private final FileRelationMapper relationMapper;
    private final UserMapper userMapper;
    private final IdGenerator idGenerator;

    public AttachmentFileApplicationService(AttachmentRuleApplicationService ruleService, ManagedFileMapper fileMapper,
                                            FileRelationMapper relationMapper, UserMapper userMapper, IdGenerator idGenerator) {
        this.ruleService = ruleService; this.fileMapper = fileMapper; this.relationMapper = relationMapper;
        this.userMapper = userMapper; this.idGenerator = idGenerator;
    }

    @Transactional
    public ManagedFile registerUpload(RegisterAttachmentFileCommand command) {
        Objects.requireNonNull(command, "附件登记请求不能为空");
        requireUser(command.uploaderId());
        ruleService.validateNewFiles(command.moduleCode(), command.fileCategory(), List.of(
                new AttachmentCandidate(command.originalName(), command.contentType(), command.sizeBytes())
        ));
        String name = required(command.originalName(), "文件名称", 255);
        ManagedFileRecord record = new ManagedFileRecord(idGenerator.nextId(), newStorageKey(), name, extensionOf(name),
                required(command.contentType(), "内容类型", 100), command.sizeBytes(), command.uploaderId(), FileStatus.UPLOADING, null, null, null);
        if (fileMapper.insert(record) != 1) throw new IllegalStateException("附件元数据保存失败");
        return toFile(record);
    }

    @Transactional
    public ManagedFile completeUpload(CompleteAttachmentUploadCommand command) {
        Objects.requireNonNull(command, "附件完成确认请求不能为空");
        ManagedFileRecord record = requireFile(command.fileId());
        if (record.status() != FileStatus.UPLOADING) throw new IllegalStateException("附件未处于待完成状态");
        if (record.sizeBytes() != command.sizeBytes() || !record.contentType().equals(required(command.contentType(), "内容类型", 100))) {
            throw new IllegalArgumentException("上传完成元数据与登记信息不一致");
        }
        if (fileMapper.markAvailable(record.id()) != 1) throw new IllegalStateException("附件完成确认失败");
        return toFile(requireFile(record.id()));
    }

    @Transactional
    public FileRelation attachToBusiness(AttachFileToBusinessCommand command) {
        Objects.requireNonNull(command, "附件关联请求不能为空");
        ManagedFileRecord file = requireFile(command.fileId());
        if (file.status() != FileStatus.AVAILABLE) throw new IllegalStateException("附件未完成，不能建立业务关联");
        FileRelationRecord relation = new FileRelationRecord(idGenerator.nextId(), file.id(), code(command.moduleCode(), "模块编码"),
                requiredId(command.businessId(), "业务对象标识"), code(command.relationType(), "关联类型"),
                code(command.visibleScope(), "可见范围"), FileRelationStatus.ACTIVE, null, null);
        if (relationMapper.insert(relation) != 1) throw new IllegalStateException("附件业务关联保存失败");
        return toRelation(relation);
    }

    @Transactional
    public void releaseBusinessRelation(Long relationId) {
        FileRelationRecord relation = requireRelation(relationId);
        if (relation.status() == FileRelationStatus.RELEASED) return;
        if (relationMapper.markReleased(relation.id()) != 1) throw new IllegalStateException("附件业务关联解除失败");
    }

    public ManagedFile findFile(Long fileId) { return toFile(requireFile(fileId)); }
    public FileRelation findRelation(Long relationId) { return toRelation(requireRelation(relationId)); }

    private ManagedFileRecord requireFile(Long id) {
        ManagedFileRecord file = id == null ? null : fileMapper.findById(id);
        if (file == null) throw new IllegalArgumentException("附件不存在：" + id);
        return file;
    }
    private FileRelationRecord requireRelation(Long id) {
        FileRelationRecord relation = id == null ? null : relationMapper.findById(id);
        if (relation == null) throw new IllegalArgumentException("附件业务关联不存在：" + id);
        return relation;
    }
    private void requireUser(Long id) { if (id == null || userMapper.findById(id) == null) throw new IllegalArgumentException("上传人不存在：" + id); }
    private ManagedFile toFile(ManagedFileRecord f) { return new ManagedFile(f.id(), f.storageKey(), f.originalName(), f.extension(), f.contentType(), f.sizeBytes(), f.uploaderId(), f.status()); }
    private FileRelation toRelation(FileRelationRecord r) { return new FileRelation(r.id(), r.fileId(), r.moduleCode(), r.businessId(), r.relationType(), r.visibleScope(), r.status()); }
    private String newStorageKey() { return "attachment/" + System.currentTimeMillis() + "/" + UUID.randomUUID(); }
    private String extensionOf(String name) { int index=name.lastIndexOf('.'); if(index<=0 || index==name.length()-1) throw new IllegalArgumentException("文件名称缺少扩展名"); return name.substring(index+1).toLowerCase(Locale.ROOT); }
    private String required(String value,String field,int max) { if(value==null || value.trim().isEmpty()) throw new IllegalArgumentException(field+"不能为空"); String text=value.trim(); if(text.length()>max) throw new IllegalArgumentException(field+"长度不能超过"+max+"个字符"); return text; }
    private String code(String value,String field) { return required(value,field,64).toUpperCase(Locale.ROOT); }
    private Long requiredId(Long value,String field) { if(value==null) throw new IllegalArgumentException(field+"不能为空"); return value; }
}
