package com.lingdong.learning.organization.application;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
@Service
public class CourseCategoryDictionary {
 private final DictionaryQueryService dictionaries; private final String stageType,subjectType;
 public CourseCategoryDictionary(DictionaryQueryService dictionaries,@Value("${lingdong.course-category.stage-dictionary-type:}") String stageType,@Value("${lingdong.course-category.subject-dictionary-type:}") String subjectType){this.dictionaries=dictionaries;this.stageType=stageType;this.subjectType=subjectType;}
 public record Option(String code,String name){}
 public record Options(boolean configured,List<Option> stages,List<Option> subjects){}
 public Options options(){
  if(stageType.isBlank()||subjectType.isBlank())return new Options(false,List.of(),List.of());
  var stages=dictionaries.findEnabledItems(stageType).stream().map(i->new Option(i.code(),i.name())).toList();
  var subjects=dictionaries.findEnabledItems(subjectType).stream().map(i->new Option(i.code(),i.name())).toList();
  return new Options(!stages.isEmpty()&&!subjects.isEmpty(),stages,subjects);
 }
 public void validate(List<String> stages,String subject,String description){
  var options=options();
  if(!options.configured())throw new IllegalArgumentException("课程分类学段和学科字典尚未配置");
  if(stages==null||stages.isEmpty()||stages.size()!=new java.util.HashSet<>(stages).size()||stages.stream().anyMatch(s->options.stages().stream().noneMatch(o->o.code().equals(s))))throw new IllegalArgumentException("请选择有效的适用学段");
  if(subject==null||options.subjects().stream().noneMatch(o->o.code().equals(subject)))throw new IllegalArgumentException("请选择有效的学科");
  if(description!=null&&description.length()>100)throw new IllegalArgumentException("分类描述不能超过100字");
 }
}
