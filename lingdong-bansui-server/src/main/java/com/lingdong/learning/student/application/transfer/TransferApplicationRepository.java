package com.lingdong.learning.student.application.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

/** Internal persistence only. Callers must authorize the family/institution before returning records. */
@Repository
public class TransferApplicationRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper json;
 public TransferApplicationRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
 public record Stored(long id,long familyId,int version,TransferApplication application,boolean executionBlocked,String executionErrorCode,String reviewReason,java.time.LocalDateTime reviewedAt,java.time.LocalDateTime createdAt){
  public Stored(long id,long familyId,int version,TransferApplication application){this(id,familyId,version,application,false,null,null,null,null);}
 }
 public record ExecutionIdentity(long applicantId,String applicantClient,Long reviewerId,String reviewerClient,String submissionMode,Long guardianId){
  public ExecutionIdentity(long applicantId,String applicantClient,Long reviewerId,String reviewerClient){this(applicantId,applicantClient,reviewerId,reviewerClient,"FAMILY",applicantId);}
 }
 public long uniqueGuardianFamily(long student,long guardian){
  var rows=jdbc.queryForList("SELECT DISTINCT family.id FROM edu_student child JOIN sys_user_organization child_member ON child_member.user_id=child.student_user_id JOIN sys_user_organization guardian_member ON guardian_member.organization_id=child_member.organization_id AND guardian_member.user_id=? JOIN sys_organization family ON family.id=child_member.organization_id WHERE child.id=? AND family.organization_type='FAMILY' AND family.status='ENABLED' AND family.effective_status='ENABLED' ORDER BY family.id LIMIT 2",Long.class,guardian,student);
  if(rows.size()!=1)throw new IllegalArgumentException(rows.isEmpty()?"请先建立有效家庭与主监护关系":"孩子关联多个家庭，请由主监护人申请");return rows.get(0);
 }
 public Optional<Stored> lockApplication(long id){
  if(jdbc.queryForList("SELECT id FROM edu_transfer_application WHERE id=? FOR UPDATE",Long.class,id).isEmpty())return Optional.empty();return find(id);
 }
 public ExecutionIdentity executionIdentity(long id){return jdbc.queryForObject("SELECT applicant_user_id,applicant_client_type,reviewer_user_id,reviewer_client_type,submission_mode,primary_guardian_user_id FROM edu_transfer_application WHERE id=?",(r,n)->new ExecutionIdentity(r.getLong(1),r.getString(2),r.getObject(3,Long.class),r.getString(4),r.getString(5),r.getObject(6,Long.class)),id);}
 public boolean executionBlocked(long id){return jdbc.queryForObject("SELECT execution_blocked FROM edu_transfer_application WHERE id=?",Integer.class,id)!=0;}
 public List<Long> dueIds(java.time.LocalDate today,int limit){if(limit<1||limit>100)throw new IllegalArgumentException("执行批次不合法");return jdbc.queryForList("SELECT id FROM edu_transfer_application WHERE status='APPROVED' AND execution_blocked=0 AND effective_date<=? ORDER BY effective_date,id LIMIT ?",Long.class,today,limit);}
 public boolean blockExecution(long id,int version,String code){return jdbc.update("UPDATE edu_transfer_application SET execution_blocked=1,execution_error_code=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=? AND status='APPROVED' AND execution_blocked=0",code,id,version)==1;}
 public boolean completeExecution(long id,int version,java.time.LocalDateTime now){return jdbc.update("UPDATE edu_transfer_application SET status='COMPLETED',completed_at=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=? AND status='APPROVED' AND execution_blocked=0 AND effective_date<=?",now,id,version,now.toLocalDate())==1;}
 private String lockMaterial(long actor,long file){
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("材料操作需要事务");
  var rows=jdbc.queryForList("SELECT uploader_id,module_code,file_category,status FROM sys_file WHERE id=? FOR UPDATE",file);
  if(rows.isEmpty())throw new IllegalArgumentException("材料不存在或不可操作");
  var row=rows.get(0);
  if(((Number)row.get("uploader_id")).longValue()!=actor||!"STUDENT_TRANSFER".equals(row.get("module_code"))||!"MATERIAL".equals(row.get("file_category")))throw new IllegalArgumentException("材料不存在或不可操作");
  return (String)row.get("status");
 }
 public void lockUnusedMaterials(long actor,List<Long> files){
  for(long file:files.stream().distinct().sorted().toList()){
   if(!"AVAILABLE".equals(lockMaterial(actor,file))||jdbc.queryForObject("SELECT COUNT(*) FROM sys_file_relation WHERE file_id=? AND status='ACTIVE'",Integer.class,file)>0)throw new IllegalArgumentException("材料已关联或不可使用");
  }
 }
 /** Keep the audit row; existing retired-content cleanup performs physical deletion. */
 public void retireUnusedMaterial(long actor,long file){
  String status=lockMaterial(actor,file);
  if("RETIRED".equals(status))return;
  if(!"AVAILABLE".equals(status)||jdbc.queryForObject("SELECT COUNT(*) FROM sys_file_relation WHERE file_id=? AND status='ACTIVE'",Integer.class,file)>0)throw new IllegalArgumentException("材料已关联或不可移除");
  if(jdbc.update("UPDATE sys_file SET status='RETIRED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='AVAILABLE'",file)!=1)throw new IllegalStateException("材料状态已变更");
 }
 public void insert(long id,long familyId,TransferApplication a){
  if(id<=0||familyId<=0||a.status()!=TransferApplication.Status.PENDING)throw new IllegalArgumentException("申请标识或初始状态不合法");
  String files;try{files=json.writeValueAsString(a.attachmentIds());}catch(Exception e){throw new IllegalArgumentException("附件引用格式错误",e);}
  jdbc.update("INSERT INTO edu_transfer_application(id,student_id,source_institution_id,family_organization_id,applicant_user_id,primary_guardian_user_id,target_description,reason,effective_date,attachment_ids) VALUES (?,?,?,?,?,?,?,?,?,?)",id,a.studentId(),a.sourceInstitutionId(),familyId,a.applicantId(),a.applicantId(),a.targetDescription(),a.reason(),a.effectiveDate(),files);
 }
 public void recordApplicantClient(long id,String client){validateClient(client);if(jdbc.update("UPDATE edu_transfer_application SET applicant_client_type=? WHERE id=? AND status='PENDING'",client,id)!=1)throw new IllegalStateException("申请状态已变更");}
 public void recordInstitutionSubmission(long id,long guardian,String client){validateClient(client);if(guardian<=0)throw new IllegalArgumentException("主监护人标识不合法");if(jdbc.update("UPDATE edu_transfer_application SET submission_mode='INSTITUTION',primary_guardian_user_id=?,applicant_client_type=? WHERE id=? AND status='PENDING'",guardian,client,id)!=1)throw new IllegalStateException("申请状态已变更");}
 private static void validateClient(String client){if(!"WEB".equals(client)&&!"MINIAPP".equals(client))throw new IllegalArgumentException("客户端不合法");}
 private Stored row(java.sql.ResultSet r,int n) throws java.sql.SQLException {
  List<Long> files;try{files=json.readValue(r.getString("attachment_ids"),json.getTypeFactory().constructCollectionType(List.class,Long.class));}catch(Exception e){throw new IllegalStateException("转学附件引用损坏",e);}
  var completed=r.getTimestamp("completed_at");
  return new Stored(r.getLong("id"),r.getLong("family_organization_id"),r.getInt("version_no"),new TransferApplication(r.getLong("student_id"),r.getLong("source_institution_id"),r.getLong("applicant_user_id"),r.getString("target_description"),r.getString("reason"),r.getDate("effective_date").toLocalDate(),files,TransferApplication.Status.valueOf(r.getString("status")),completed==null?null:completed.toLocalDateTime()),r.getInt("execution_blocked")!=0,r.getString("execution_error_code"),r.getString("review_reason"),time(r,"reviewed_at"),time(r,"created_at"));
 }
 private static java.time.LocalDateTime time(java.sql.ResultSet row,String column)throws java.sql.SQLException{var stamp=row.getTimestamp(column);return stamp==null?null:stamp.toLocalDateTime();}
 public Optional<Stored> find(long id){return jdbc.query("SELECT * FROM edu_transfer_application WHERE id=?",this::row,id).stream().findFirst();}
 public record Page(List<Stored> items,long total,int page,int pageSize){}
 /** The identical SQL predicate is used for rows and total; no post-pagination filtering. */
 public Page list(long actor,long organization,boolean family,String status,int page,int size){
  if(actor<=0||organization<=0||page<1||size<1||size>100)throw new IllegalArgumentException("分页参数不合法");
  if(status!=null)TransferApplication.Status.valueOf(status);
  String predicate;
  var args=new java.util.ArrayList<Object>();
  if(family){
   predicate="a.family_organization_id=? AND EXISTS (SELECT 1 FROM edu_student s JOIN sys_user_organization child ON child.user_id=s.student_user_id AND child.organization_id=a.family_organization_id JOIN sys_user_organization adult ON adult.organization_id=child.organization_id AND adult.user_id=? JOIN edu_parent_student guardian ON guardian.student_id=s.id AND guardian.parent_user_id=adult.user_id WHERE s.id=a.student_id AND guardian.status='ACTIVE' AND guardian.relation_role='PRIMARY_GUARDIAN' AND guardian.primary_scope_key='PRIMARY')";
   args.add(organization);args.add(actor);
  }else{predicate="a.source_institution_id=?";args.add(organization);}
  if(status!=null){predicate+=" AND a.status=?";args.add(status);}
  long total=jdbc.queryForObject("SELECT COUNT(*) FROM edu_transfer_application a WHERE "+predicate,Long.class,args.toArray());
  args.add(size);args.add(Math.multiplyExact((long)page-1,size));
  var items=jdbc.query("SELECT a.* FROM edu_transfer_application a WHERE "+predicate+" ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",this::row,args.toArray());
  return new Page(List.copyOf(items),total,page,size);
 }
 /** Version + expected status prevents stale approval and competing withdrawal. */
 boolean review(long id,int version,long reviewer,boolean approved,String reason){return review(id,version,reviewer,approved,reason,"MINIAPP");}
 public boolean review(long id,int version,long reviewer,boolean approved,String reason,String client){
  validateClient(client);
  if(version<=0||reviewer<=0||(!approved&&(reason==null||reason.isBlank()))||(reason!=null&&reason.length()>200))throw new IllegalArgumentException("审核参数不合法");
  return jdbc.update("UPDATE edu_transfer_application SET status=?,reviewer_user_id=?,reviewer_client_type=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=? AND status='PENDING' AND applicant_user_id<>?",approved?"APPROVED":"REJECTED",reviewer,client,reason,id,version,reviewer)==1;
 }
 public boolean withdraw(long id,int version,long applicant){return jdbc.update("UPDATE edu_transfer_application SET status='WITHDRAWN',version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=? AND status='PENDING' AND applicant_user_id=?",id,version,applicant)==1;}
 public boolean rejectBlocked(long id,int version,long reviewer,String reason,String client){
  validateClient(client);if(reason==null||reason.isBlank()||reason.length()>200)throw new IllegalArgumentException("请填写200字以内驳回原因");
  return jdbc.update("UPDATE edu_transfer_application SET status='REJECTED',reviewer_user_id=?,reviewer_client_type=?,review_reason=?,reviewed_at=CURRENT_TIMESTAMP,execution_blocked=0,execution_error_code=NULL,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version_no=? AND status='APPROVED' AND execution_blocked=1 AND applicant_user_id<>?",reviewer,client,reason,id,version,reviewer)==1;
 }
}
