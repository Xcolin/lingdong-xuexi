package com.lingdong.learning.growthpoint.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** 内部班级汇总；调用方必须先完成家长、当前班级、权限及主动开启检查。 */
@Mapper
public interface AnonymousRankMapper {
    List<Row> list(@Param("classId") Long classId);
    List<ClassOption> classes(@Param("studentId") Long studentId);
    record ClassOption(String classId, String className) { }
    /** 排名行不携带姓名、学生标识或可关联的匿名标识。 */
    record Row(Long rank, Long points) { }
}
