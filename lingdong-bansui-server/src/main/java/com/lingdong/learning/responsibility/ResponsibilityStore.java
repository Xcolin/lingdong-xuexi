package com.lingdong.learning.responsibility;

import org.springframework.jdbc.core.JdbcTemplate;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

/** All queries bind both student and institution; history survives personnel departure. */
@Repository
public class ResponsibilityStore {
    private final JdbcTemplate jdbc;
    public ResponsibilityStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Person(String id,String name){}
    public record Row(Long userId,int version){}
    public record Fallback(Person person,String className){}
    public record Transfer(String fromUserId,String fromName,String toUserId,String toName,String reasonType,String reasonText,
                           String transferredBy,String operatorName,int beforeVersionNo,int afterVersionNo,LocalDateTime createdAt){}
    private static final String VALID_ORG=" o.status='ENABLED' AND o.effective_status='ENABLED' ";
    private static final String VALID_REL=" r.status='ACTIVE' AND r.effective_from<=CURRENT_TIMESTAMP AND (r.effective_to IS NULL OR r.effective_to>CURRENT_TIMESTAMP) ";
    // The administrator relation defines authority; an enabled scoped role must still exist.
    // No built-in role name grants a bypass or excludes custom administrator roles.
    private static final String VALID_ADMIN_ROLE=" EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role enabled_role ON enabled_role.id=ur.role_id AND enabled_role.status='ENABLED' WHERE ur.user_id=a.user_id AND ur.organization_id=a.organization_id) ";
    public boolean enrolled(long student,long org){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM edu_student_organization r JOIN sys_organization o ON o.id=r.organization_id WHERE r.student_id=? AND r.organization_id=? AND r.relation_type='ENROLLMENT' AND "+VALID_REL+" AND "+VALID_ORG+" AND o.organization_type IN ('SCHOOL','TRAINING_INSTITUTION'))",Boolean.class,student,org));
    }
    public boolean guardian(long user,long student){
        var selected=SelectedOrganizationContext.organization();
        if(selected!=null&&!"FAMILY".equals(selected.typeCode()))return false;
        String selectedScope=selected==null?"":" AND family.id=?";
        Object[] args=selected==null?new Object[]{user,student}:new Object[]{user,student,selected.id()};
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM edu_parent_student p JOIN sys_user u ON u.id=p.parent_user_id AND u.status='ENABLED' JOIN edu_student child ON child.id=p.student_id AND child.status='ENABLED' JOIN sys_user_organization parent_member ON parent_member.user_id=p.parent_user_id JOIN sys_user_organization child_member ON child_member.user_id=child.student_user_id AND child_member.organization_id=parent_member.organization_id JOIN sys_organization family ON family.id=parent_member.organization_id AND family.organization_type='FAMILY' AND family.status='ENABLED' AND family.effective_status='ENABLED' WHERE p.parent_user_id=? AND p.student_id=? AND p.status='ACTIVE'"+selectedScope+")",Boolean.class,args));
    }
    public boolean activeAdministrator(long user,long org){
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM sys_organization_admin a JOIN sys_user u ON u.id=a.user_id AND u.status='ENABLED' JOIN sys_organization o ON o.id=a.organization_id WHERE a.user_id=? AND a.organization_id=? AND "+VALID_ORG+" AND "+VALID_ADMIN_ROLE+")",Boolean.class,user,org));
    }
    public List<Person> candidates(long org){
        return jdbc.query("SELECT DISTINCT u.id,u.display_name FROM sys_organization_admin a JOIN sys_user u ON u.id=a.user_id AND u.status='ENABLED' JOIN sys_organization o ON o.id=a.organization_id WHERE a.organization_id=? AND "+VALID_ORG+" AND "+VALID_ADMIN_ROLE+" ORDER BY u.display_name,u.id",(r,n)->new Person(r.getString(1),r.getString(2)),org);
    }
    public Row row(long student,long org){
        return jdbc.query("SELECT responsible_user_id,version_no FROM edu_student_institution_responsibility WHERE student_id=? AND institution_organization_id=?",(r,n)->new Row(r.getObject(1,Long.class),r.getInt(2)),student,org).stream().findFirst().orElse(null);
    }
    public Person person(long id){return jdbc.query("SELECT id,display_name FROM sys_user WHERE id=?",(r,n)->new Person(r.getString(1),r.getString(2)),id).stream().findFirst().orElse(new Person(Long.toString(id),null));}
    public Fallback fallback(long student,long org){
        return jdbc.query("SELECT u.id,u.display_name,o.organization_name FROM edu_student_organization r JOIN sys_organization o ON o.id=r.organization_id JOIN sys_organization institution ON institution.id=? AND LEFT(o.organization_path,CHAR_LENGTH(institution.organization_path))=institution.organization_path JOIN edu_class_academic_metadata m ON m.class_organization_id=o.id JOIN sys_user u ON u.id=m.head_teacher_user_id AND u.status='ENABLED' JOIN edu_teacher_class t ON t.teacher_user_id=u.id AND t.class_organization_id=o.id AND t.status='ACTIVE' AND t.effective_from<=CURRENT_TIMESTAMP AND (t.effective_to IS NULL OR t.effective_to>CURRENT_TIMESTAMP) WHERE r.student_id=? AND r.relation_type='CLASS' AND o.organization_type='CLASS' AND "+VALID_REL+" AND "+VALID_ORG+" ORDER BY r.effective_from DESC,r.id DESC LIMIT 1",(r,n)->new Fallback(new Person(r.getString(1),r.getString(2)),r.getString(3)),org,student).stream().findFirst().orElse(null);
    }
    public void insert(long student,long org,long target,long actor){jdbc.update("INSERT INTO edu_student_institution_responsibility(student_id,institution_organization_id,responsible_user_id,version_no,updated_by) VALUES(?,?,?,1,?)",student,org,target,actor);}
    public boolean update(long student,long org,long target,long actor,int expected){return jdbc.update("UPDATE edu_student_institution_responsibility SET responsible_user_id=?,version_no=version_no+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE student_id=? AND institution_organization_id=? AND version_no=?",target,actor,student,org,expected)==1;}
    public void history(long student,long org,Long from,long to,String reason,String text,long actor,int before,int after){
        jdbc.update("INSERT INTO edu_student_responsibility_transfer(student_id,institution_organization_id,from_user_id,to_user_id,reason_type,reason_text,transferred_by,before_version_no,after_version_no) VALUES(?,?,?,?,?,?,?,?,?)",student,org,from,to,reason,text,actor,before,after);
    }
    public long historyCount(long student,long org){return jdbc.queryForObject("SELECT COUNT(*) FROM edu_student_responsibility_transfer WHERE student_id=? AND institution_organization_id=?",Long.class,student,org);}
    public List<Transfer> historyPage(long student,long org,int limit,long offset){return jdbc.query("SELECT h.from_user_id,f.display_name,h.to_user_id,t.display_name,h.reason_type,h.reason_text,h.transferred_by,a.display_name,h.before_version_no,h.after_version_no,h.created_at FROM edu_student_responsibility_transfer h LEFT JOIN sys_user f ON f.id=h.from_user_id LEFT JOIN sys_user t ON t.id=h.to_user_id LEFT JOIN sys_user a ON a.id=h.transferred_by WHERE h.student_id=? AND h.institution_organization_id=? ORDER BY h.after_version_no DESC LIMIT ? OFFSET ?",(r,n)->new Transfer(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getInt(9),r.getInt(10),r.getTimestamp(11).toLocalDateTime()),student,org,limit,offset);}
}
