package com.lingdong.learning.studentdirectory;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class InstitutionStudentDirectoryDao {
 private final NamedParameterJdbcTemplate jdbc;
 public InstitutionStudentDirectoryDao(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 public record Query(Long institutionId,List<Long> classIds,boolean institutionScope,String keyword,Long studentId,String status){}
 public record Row(Long studentId,String studentName,String studentNumber,String status){}
 private String where(Query q){
  String clause=" FROM edu_student s LEFT JOIN edu_student_enrollment_metadata m ON m.student_id=s.id AND m.institution_organization_id=:institution WHERE s.status <> 'CANCELLED' AND EXISTS (SELECT 1 FROM edu_student_organization e WHERE e.student_id=s.id AND e.organization_id=:institution AND e.relation_type='ENROLLMENT' AND e.status IN ('ACTIVE','INACTIVE')";
  if(!"ALL".equals(q.status()))clause+=" AND e.status=:status";
  clause+=")";
  if(!q.institutionScope()) clause+=q.classIds().isEmpty()?" AND 1=0":" AND EXISTS (SELECT 1 FROM edu_student_organization c WHERE c.student_id=s.id AND c.relation_type='CLASS' AND c.status='ACTIVE' AND c.organization_id IN (:classes))";
  if(q.keyword()!=null&&!q.keyword().isBlank())clause+=" AND (LOWER(s.student_name) LIKE :keyword ESCAPE '!' OR LOWER(m.student_number) LIKE :keyword ESCAPE '!')";
  if(q.studentId()!=null)clause+=" AND s.id=:student";
  return clause;
 }
 private MapSqlParameterSource params(Query q){return new MapSqlParameterSource().addValue("institution",q.institutionId()).addValue("classes",q.classIds()).addValue("status",q.status()).addValue("student",q.studentId()).addValue("keyword",q.keyword()==null?null:"%"+q.keyword().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
 public long count(Query q){return jdbc.queryForObject("SELECT COUNT(*)"+where(q),params(q),Long.class);}
 public List<Row> page(Query q,int offset,int size){
  String enrollmentStatus="CASE WHEN EXISTS(SELECT 1 FROM edu_student_organization active_enrollment WHERE active_enrollment.student_id=s.id AND active_enrollment.organization_id=:institution AND active_enrollment.relation_type='ENROLLMENT' AND active_enrollment.status='ACTIVE') THEN 'ACTIVE' ELSE 'INACTIVE' END";
  return jdbc.query("SELECT s.id,s.student_name,m.student_number,"+enrollmentStatus+" AS enrollment_status"+where(q)+" ORDER BY s.student_name,s.id LIMIT :size OFFSET :offset",params(q).addValue("size",size).addValue("offset",offset),(rs,n)->new Row(rs.getLong("id"),rs.getString("student_name"),rs.getString("student_number"),rs.getString("enrollment_status")));
 }
 public record ClassMembership(Long studentId,Long classId){}
 public List<ClassMembership> classes(List<Long> studentIds,List<Long> classIds){
  if(studentIds.isEmpty()||classIds.isEmpty())return List.of();
  return jdbc.query("SELECT DISTINCT student_id,organization_id FROM edu_student_organization WHERE student_id IN (:students) AND organization_id IN (:classes) AND relation_type='CLASS' AND status='ACTIVE'",new MapSqlParameterSource().addValue("students",studentIds).addValue("classes",classIds),(rs,n)->new ClassMembership(rs.getLong(1),rs.getLong(2)));
 }
}
