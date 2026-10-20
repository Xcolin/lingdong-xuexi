package com.lingdong.learning.organization.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.organization.domain.Organization;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
@Service
public class CourseCategoryService {
 private final ClassManagementApplicationService classes;private final CourseCategoryDictionary dictionary;private final JdbcTemplate jdbc;private final ObjectMapper json;
 public CourseCategoryService(ClassManagementApplicationService classes,CourseCategoryDictionary dictionary,JdbcTemplate jdbc,ObjectMapper json){this.classes=classes;this.dictionary=dictionary;this.jdbc=jdbc;this.json=json;}
 public record Metadata(List<String> stageCodes,String subjectCode,String description){}
 private void requireTraining(Organization node){var root=classes.ancestor(node,"SCHOOL");if(root==null||(!"TRAINING_INSTITUTION".equals(root.typeCode())&&!"SCHOOL".equals(root.typeCode()))||!"ENABLED".equals(node.effectiveStatus().name()))throw new IllegalArgumentException("仅正常学校或培训机构课程分类支持分类元数据");}
 private void validate(Metadata data){if(data==null)throw new IllegalArgumentException("请填写课程分类信息");dictionary.validate(data.stageCodes(),data.subjectCode(),data.description());}
 private void persist(Long id,Metadata data){try{var stages=json.writeValueAsString(data.stageCodes());jdbc.update("DELETE FROM org_course_category_metadata WHERE organization_id=?",id);jdbc.update("INSERT INTO org_course_category_metadata(organization_id,stage_codes,subject_code,description) VALUES (?,?,?,?)",id,stages,data.subjectCode(),data.description()==null?"":data.description());}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("学段格式错误",e);}}
 @Transactional public Organization create(AuthenticatedUser user,Long schoolId,Long campusId,String name,Integer sort,Metadata data){validate(data);var node=classes.createGrade(user,schoolId,campusId,name,sort);requireTraining(node);persist(node.id(),data);return node;}
 @Transactional public Organization update(AuthenticatedUser user,Long id,String name,Integer sort,Integer version,Metadata data){validate(data);var node=classes.updateGrade(user,new UpdateClassCommand(id,name,sort,version));requireTraining(node);persist(id,data);return node;}
 public Metadata read(AuthenticatedUser user,Long id){var node=classes.listGrades(user,null).stream().filter(n->n.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("课程分类不存在或不可访问"));requireTraining(node);return jdbc.query("SELECT stage_codes,subject_code,description FROM org_course_category_metadata WHERE organization_id=?",(rs,n)->{try{return new Metadata(json.readValue(rs.getString(1),json.getTypeFactory().constructCollectionType(List.class,String.class)),rs.getString(2),rs.getString(3));}catch(Exception e){throw new IllegalStateException("课程分类数据格式错误",e);}},id).stream().findFirst().orElse(null);}
}
