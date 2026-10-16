package com.lingdong.learning.organization.onboarding.infrastructure.persistence;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;
@Mapper
public interface OnboardingMapper {
 @Select("SELECT id,applicant_id applicantId,type_code typeCode,name,normalized_name normalizedName,region_id regionId,contact_name contactName,mobile,email,status,task_id taskId,organization_id organizationId,rejection_reason rejectionReason,submission_batch submissionBatch,version_no versionNo,created_at createdAt,updated_at updatedAt FROM sys_organization_onboarding_application WHERE id=#{id} FOR UPDATE")
 Map<String,Object> lock(@Param("id") Long id);
 @Select("SELECT id FROM sys_organization_onboarding_application WHERE applicant_id=#{userId} ORDER BY created_at DESC")
 List<Long> ids(@Param("userId") Long userId);
 @Select("SELECT id FROM sys_organization_onboarding_application WHERE task_id=#{taskId}")
 Long byTask(@Param("taskId") Long taskId);
 @Insert("INSERT INTO sys_organization_onboarding_application(id,applicant_id,type_code,name,normalized_name,region_id,contact_name,mobile,email,status,task_id) VALUES(#{id},#{userId},#{typeCode},#{name},#{normalizedName},#{regionId},#{contactName},#{mobile},#{email},'PENDING',#{taskId})")
 int insert(Map<String,Object> row);
 @Update("UPDATE sys_organization_onboarding_application SET status=#{status},organization_id=#{organizationId},rejection_reason=#{reason},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status='PENDING' AND version_no=#{versionNo} AND submission_batch=#{submissionBatch}")
 int transition(Map<String,Object> row);
 @Update("UPDATE sys_organization_onboarding_application SET type_code=#{typeCode},name=#{name},normalized_name=#{normalizedName},region_id=#{regionId},contact_name=#{contactName},mobile=#{mobile},email=#{email},status='PENDING',task_id=#{taskId},rejection_reason=NULL,version_no=version_no+1,submission_batch=submission_batch+1,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND status IN ('REJECTED','WITHDRAWN') AND version_no=#{versionNo} AND submission_batch=#{submissionBatch}")
 int resubmit(Map<String,Object> row);
 @Insert("INSERT INTO sys_organization_onboarding_event(id,application_id,submission_batch,event_type,operator_id,reason) VALUES(#{id},#{applicationId},#{batch},#{event},#{userId},#{reason})")
 int event(@Param("id") Long id,@Param("applicationId") Long applicationId,@Param("batch") int batch,@Param("event") String event,@Param("userId") Long userId,@Param("reason") String reason);
 @Insert("INSERT INTO sys_organization_onboarding_institution_key(type_code,normalized_name,region_id,application_id) VALUES(#{typeCode},#{normalizedName},#{regionId},#{id})")
 int reserve(Map<String,Object> row);
 @Delete("DELETE FROM sys_organization_onboarding_institution_key WHERE application_id=#{id}")
 int release(@Param("id") Long id);
 @Select("SELECT organization_id FROM sys_organization_onboarding_family_request WHERE user_id=#{userId} AND idempotency_key=#{key}")
 Long family(@Param("userId") Long userId,@Param("key") String key);
 @Insert("INSERT INTO sys_organization_onboarding_family_request(user_id,idempotency_key,organization_id,name) VALUES(#{userId},#{key},#{organizationId},#{name})")
 int familyRequest(@Param("userId") Long userId,@Param("key") String key,@Param("organizationId") Long organizationId,@Param("name") String name);
 @Select("SELECT COUNT(*) FROM sys_organization WHERE organization_type=#{typeCode} AND parent_id=#{regionId} AND LOWER(TRIM(organization_name))=#{normalizedName}")
 int existing(Map<String,Object> row);
 @Insert("INSERT INTO sys_organization_owner(organization_id,user_id) VALUES(#{organizationId},#{userId})")
 int owner(@Param("organizationId") Long organizationId,@Param("userId") Long userId);
 @Select("SELECT user_id FROM sys_organization_owner WHERE organization_id=#{organizationId}")
 Long ownerId(@Param("organizationId") Long organizationId);
 @Select("SELECT DISTINCT r.role_code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId} AND ur.organization_id=#{organizationId} AND r.status='ENABLED' ORDER BY r.role_code")
 List<String> roleCodes(@Param("userId") Long userId,@Param("organizationId") Long organizationId);
}
