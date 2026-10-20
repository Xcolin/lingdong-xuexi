package com.lingdong.learning.student.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface InstitutionEnrollmentMapper {
    record Invitation(Long id, String codeHash, Long institutionOrganizationId, Long classOrganizationId,
            Long createdBy, LocalDateTime expiresAt, String status, Integer maxUses, Integer usedCount) {}
    record Request(@com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long id, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long studentId, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long studentUserId, String studentName, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long institutionOrganizationId,
            String institutionName, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long classOrganizationId, String className, @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long familyOrganizationId,
            @com.fasterxml.jackson.databind.annotation.JsonSerialize(using=com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class) Long requestedBy, String applicantName, String applicantMobile, String status, LocalDateTime createdAt, LocalDateTime reviewedAt, String reviewReason) {}
    @Insert("INSERT INTO edu_institution_invitation(id,code_hash,institution_organization_id,class_organization_id,created_by,expires_at,status,max_uses,used_count) VALUES(#{id},#{codeHash},#{institutionOrganizationId},#{classOrganizationId},#{createdBy},#{expiresAt},'ACTIVE',#{maxUses},0)")
    int insertInvitation(Invitation invitation);
    @Select("SELECT id,code_hash,institution_organization_id,class_organization_id,created_by,expires_at,status,max_uses,used_count FROM edu_institution_invitation WHERE code_hash=#{hash} FOR UPDATE")
    Invitation lockInvitation(String hash);
    @Select("SELECT id,code_hash,institution_organization_id,class_organization_id,created_by,expires_at,status,max_uses,used_count FROM edu_institution_invitation WHERE id=#{id} FOR UPDATE")
    Invitation lockInvitationById(Long id);
    @Update("UPDATE edu_institution_invitation SET expires_at=#{expiresAt} WHERE id=#{id} AND status='ACTIVE'")
    int updateInvitationExpiry(@Param("id") Long id,@Param("expiresAt") LocalDateTime expiresAt);
    @Update("UPDATE edu_institution_invitation SET used_count=used_count+1 WHERE id=#{id} AND status='ACTIVE' AND used_count<max_uses")
    int consume(Long id);
    @Select("SELECT COUNT(*) FROM edu_institution_enrollment_request WHERE student_id=#{studentId} AND class_organization_id=#{classId} AND status='PENDING'")
    int pending(@Param("studentId") Long studentId, @Param("classId") Long classId);
    @Insert("INSERT INTO edu_institution_enrollment_request(id,invitation_id,student_id,family_organization_id,institution_organization_id,class_organization_id,requested_by,status,created_at) VALUES(#{id},#{invitationId},#{studentId},#{familyId},#{institutionId},#{classId},#{actorId},'PENDING',#{now})")
    int insertRequest(@Param("id") Long id,@Param("invitationId") Long invitationId,@Param("studentId") Long studentId,
            @Param("familyId") Long familyId,@Param("institutionId") Long institutionId,@Param("classId") Long classId,
            @Param("actorId") Long actorId,@Param("now") LocalDateTime now);
    String SELECT_REQUEST = "SELECT r.id,r.student_id,s.student_user_id,s.student_name,r.institution_organization_id,i.organization_name AS institution_name,r.class_organization_id,c.organization_name AS class_name,r.family_organization_id,r.requested_by,u.display_name AS applicant_name,u.mobile AS applicant_mobile,r.status,r.created_at,r.reviewed_at,r.review_reason FROM edu_institution_enrollment_request r JOIN edu_student s ON s.id=r.student_id JOIN sys_organization i ON i.id=r.institution_organization_id JOIN sys_organization c ON c.id=r.class_organization_id LEFT JOIN sys_user u ON u.id=r.requested_by";
    @Select(SELECT_REQUEST+" WHERE r.id=#{id} FOR UPDATE") Request lockRequest(Long id);
    @Select(SELECT_REQUEST+" WHERE (#{institutionId} IS NULL OR r.institution_organization_id=#{institutionId}) AND (#{classId} IS NULL OR r.class_organization_id=#{classId}) AND (#{familyId} IS NULL OR r.family_organization_id=#{familyId}) AND (#{status} IS NULL OR r.status=#{status}) ORDER BY r.created_at DESC,r.id DESC")
    List<Request> list(@Param("institutionId") Long institutionId,@Param("classId") Long classId,@Param("familyId") Long familyId,@Param("status") String status);
    @Update("UPDATE edu_institution_enrollment_request SET status=#{status},reviewed_by=#{actorId},reviewed_at=#{now},review_reason=#{reason} WHERE id=#{id} AND status='PENDING'")
    int review(@Param("id") Long id,@Param("status") String status,@Param("actorId") Long actorId,@Param("now") LocalDateTime now,@Param("reason") String reason);
    @Update("UPDATE edu_student_organization SET status='ACTIVE',effective_from=CURRENT_TIMESTAMP,effective_to=NULL,updated_at=CURRENT_TIMESTAMP WHERE student_id=#{studentId} AND organization_id=#{institutionId} AND relation_type='ENROLLMENT'")
    int activateEnrollment(@Param("studentId") Long studentId,@Param("institutionId") Long institutionId);
    @Select("SELECT EXISTS(SELECT 1 FROM sys_user_organization m JOIN sys_organization o ON o.id=m.organization_id WHERE m.user_id=#{userId} AND o.organization_type='FAMILY' AND o.id=#{familyId} AND o.status='ENABLED' AND o.effective_status='ENABLED')")
    boolean familyMember(@Param("userId") Long userId,@Param("familyId") Long familyId);
}


