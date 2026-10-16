package com.lingdong.learning.teacher.application;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.List;
@Service
public class TeacherSubjectDictionary {
 private final DictionaryQueryService dictionaries;private final String subjectType;
 public TeacherSubjectDictionary(DictionaryQueryService dictionaries,@Value("${lingdong.teacher.subject-dictionary-type:${lingdong.course-category.subject-dictionary-type:}}") String subjectType){this.dictionaries=dictionaries;this.subjectType=subjectType==null?"":subjectType.trim();}
 public record Option(String code,String name){}
 public List<Option> options(){if(subjectType.isBlank())return List.of();return dictionaries.findEnabledItems(subjectType).stream().map(i->new Option(i.code(),i.name())).toList();}
 public String validate(String code){if(code==null)return null;String normalized=code.trim();if(normalized.isEmpty()||normalized.length()>64||options().stream().noneMatch(i->i.code().equals(normalized)))throw new IllegalArgumentException("请选择有效的任教学科");return normalized;}
}
