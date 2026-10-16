package com.lingdong.learning.student.infrastructure.persistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
/** Storage only. Application services must authorize and lock the actual class/enrollment first. */
@Repository
public class AcademicMetadataStore {
 private final JdbcTemplate jdbc;
 public AcademicMetadataStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record ClassMetadata(long classId,Long headTeacherUserId,int versionNo){}
 public record EnrollmentMetadata(long studentId,long institutionId,String studentNumber,int versionNo){}
 public Optional<ClassMetadata> classMetadata(long classId){
  return jdbc.query("SELECT class_organization_id,head_teacher_user_id,version_no FROM edu_class_academic_metadata WHERE class_organization_id=?",(r,n)->new ClassMetadata(r.getLong(1),r.getObject(2,Long.class),r.getInt(3)),classId).stream().findFirst();
 }
 public Optional<EnrollmentMetadata> enrollmentMetadata(long studentId,long institutionId){
  return jdbc.query("SELECT student_id,institution_organization_id,student_number,version_no FROM edu_student_enrollment_metadata WHERE student_id=? AND institution_organization_id=?",(r,n)->new EnrollmentMetadata(r.getLong(1),r.getLong(2),r.getString(3),r.getInt(4)),studentId,institutionId).stream().findFirst();
 }
 public void insertClass(long classId,Long headTeacherUserId){jdbc.update("INSERT INTO edu_class_academic_metadata(class_organization_id,head_teacher_user_id) VALUES(?,?)",classId,headTeacherUserId);}
 public boolean updateClass(long classId,Long headTeacherUserId,int expectedVersion){
  return jdbc.update("UPDATE edu_class_academic_metadata SET head_teacher_user_id=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE class_organization_id=? AND version_no=?",headTeacherUserId,classId,expectedVersion)==1;
 }
 public void insertEnrollment(long studentId,long institutionId,String studentNumber){jdbc.update("INSERT INTO edu_student_enrollment_metadata(student_id,institution_organization_id,student_number) VALUES(?,?,?)",studentId,institutionId,studentNumber);}
 public boolean updateEnrollment(long studentId,long institutionId,String studentNumber,int expectedVersion){
  return jdbc.update("UPDATE edu_student_enrollment_metadata SET student_number=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE student_id=? AND institution_organization_id=? AND version_no=?",studentNumber,studentId,institutionId,expectedVersion)==1;
 }
 public void recordAudit(String kind,long targetId,long scopeId,int version,long operatorId,String before,String after){
  if(!java.util.Set.of("HEAD_TEACHER","STUDENT_NUMBER").contains(kind))throw new IllegalArgumentException("不支持的配置审计类型");
  if(jdbc.update("INSERT INTO edu_academic_metadata_audit(metadata_kind,target_id,scope_id,version_no,operator_user_id,before_value,after_value) VALUES(?,?,?,?,?,?,?)",kind,targetId,scopeId,version,operatorId,before,after)!=1)throw new IllegalStateException("配置审计保存失败");
 }
 public record HeadTeacherOption(long userId,String displayName,long classId){}
 public java.util.List<HeadTeacherOption> headTeacherOptions(long classId){
  return jdbc.query("SELECT u.id,u.display_name,tc.class_organization_id FROM edu_teacher_class tc JOIN sys_user u ON u.id=tc.teacher_user_id AND u.status='ENABLED' JOIN edu_teacher_profile p ON p.user_id=u.id AND p.status='ENABLED' WHERE tc.class_organization_id=? AND tc.status='ACTIVE' AND tc.effective_to IS NULL ORDER BY u.display_name,u.id",(r,n)->new HeadTeacherOption(r.getLong(1),r.getString(2),r.getLong(3)),classId);
 }
}
