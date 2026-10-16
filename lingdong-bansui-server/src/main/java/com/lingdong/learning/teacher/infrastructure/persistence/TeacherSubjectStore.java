package com.lingdong.learning.teacher.infrastructure.persistence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
/** Storage boundary; callers authorize the institution and teacher before any access. */
@Repository
public class TeacherSubjectStore {
 private final JdbcTemplate jdbc;
 public TeacherSubjectStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Metadata(long teacherUserId,long institutionOrganizationId,String subjectCode,int versionNo){}
 public record Key(long teacherUserId,long institutionOrganizationId){}
 public java.util.Map<Key,Integer> readClassCounts(java.util.List<Key> keys){
  if(keys.isEmpty())return java.util.Map.of();
  if(keys.size()>100)throw new IllegalArgumentException("一次最多读取100位教师班级数");
  var arguments=new java.util.ArrayList<Object>();var predicates=new java.util.ArrayList<String>();
  for(var key:keys.stream().distinct().toList()){predicates.add("(relation.teacher_user_id=? AND institution.id=?)");arguments.add(key.teacherUserId());arguments.add(key.institutionOrganizationId());}
  var counts=new java.util.HashMap<Key,Integer>();
  jdbc.query("SELECT relation.teacher_user_id,institution.id,COUNT(DISTINCT classroom.id) AS class_count FROM edu_teacher_class relation JOIN sys_organization classroom ON classroom.id=relation.class_organization_id JOIN sys_organization institution ON classroom.organization_path LIKE CONCAT(institution.organization_path,'%') WHERE relation.status='ACTIVE' AND relation.effective_to IS NULL AND classroom.organization_type='CLASS' AND institution.organization_type IN ('SCHOOL','TRAINING_INSTITUTION') AND ("+String.join(" OR ",predicates)+") GROUP BY relation.teacher_user_id,institution.id",rs->{counts.put(new Key(rs.getLong(1),rs.getLong(2)),rs.getInt(3));},arguments.toArray());
  return counts;
 }
 public java.util.List<Metadata> readBatch(java.util.List<Key> keys){
  if(keys.isEmpty())return java.util.List.of();
  if(keys.size()>100)throw new IllegalArgumentException("一次最多读取100位教师学科");
  var distinct=keys.stream().distinct().toList();var arguments=new java.util.ArrayList<Object>();
  var predicates=new java.util.ArrayList<String>();
  for(var key:distinct){predicates.add("(teacher_user_id=? AND institution_organization_id=?)");arguments.add(key.teacherUserId());arguments.add(key.institutionOrganizationId());}
  return jdbc.query("SELECT teacher_user_id,institution_organization_id,subject_code,version_no FROM edu_teacher_subject_metadata WHERE "+String.join(" OR ",predicates),(r,n)->new Metadata(r.getLong(1),r.getLong(2),r.getString(3),r.getInt(4)),arguments.toArray());
 }
 public Optional<Metadata> read(long teacher,long institution){return jdbc.query("SELECT teacher_user_id,institution_organization_id,subject_code,version_no FROM edu_teacher_subject_metadata WHERE teacher_user_id=? AND institution_organization_id=?",(r,n)->new Metadata(r.getLong(1),r.getLong(2),r.getString(3),r.getInt(4)),teacher,institution).stream().findFirst();}
 public void insert(long teacher,long institution,String subject){jdbc.update("INSERT INTO edu_teacher_subject_metadata(teacher_user_id,institution_organization_id,subject_code) VALUES(?,?,?)",teacher,institution,subject);}
 public void recordAudit(long teacher,long institution,int version,long operator,String before,String after){jdbc.update("INSERT INTO edu_teacher_subject_audit(teacher_user_id,institution_organization_id,version_no,operator_user_id,before_subject_code,after_subject_code) VALUES(?,?,?,?,?,?)",teacher,institution,version,operator,before,after);}
 public boolean update(long teacher,long institution,String subject,int version){return jdbc.update("UPDATE edu_teacher_subject_metadata SET subject_code=?,version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE teacher_user_id=? AND institution_organization_id=? AND version_no=?",subject,teacher,institution,version)==1;}
}
