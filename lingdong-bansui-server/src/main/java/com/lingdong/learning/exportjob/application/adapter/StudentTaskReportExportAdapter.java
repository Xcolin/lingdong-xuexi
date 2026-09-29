package com.lingdong.learning.exportjob.application.adapter;
import com.lingdong.learning.exportjob.application.template.ExportColumnDefinition;
import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.exportjob.infrastructure.persistence.StudentTaskExportMapper;
import com.lingdong.learning.exportjob.infrastructure.persistence.StudentTaskExportRow;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
@Component
public class StudentTaskReportExportAdapter implements ExportDatasetAdapter {
 private final StudentTaskExportMapper mapper;
 public StudentTaskReportExportAdapter(StudentTaskExportMapper mapper){this.mapper=mapper;}
 public ExportJobType type(){return ExportJobType.STUDENT_TASK_REPORT;}
 public boolean sensitive(){return false;}
 public List<ExportColumnDefinition> columns(){return List.of(new ExportColumnDefinition("STUDENT_NAME","学生",true),new ExportColumnDefinition("TASK_TITLE","任务",true),new ExportColumnDefinition("SOURCE_TYPE","来源",true),new ExportColumnDefinition("STATUS","状态",true),new ExportColumnDefinition("POINTS","积分",true),new ExportColumnDefinition("REVIEW_STATUS","审核状态",true));}
 public long captureUpperBound(ExportRequestDefinition request){requireScope(request);return request.studentTaskAssignmentIds().stream().mapToLong(Long::longValue).max().orElse(0);}
 public long count(ExportRequestDefinition request,long upper){requireScope(request);return mapper.count(request,upper);}
 public ExportDataPage fetchAfter(ExportRequestDefinition request,long upper,long cursor,int limit){
  requireScope(request);if(limit<1||limit==Integer.MAX_VALUE||upper<0||cursor<0)throw new IllegalArgumentException("任务报表分页参数无效");
  var rows=mapper.findAfter(request,upper,cursor,limit+1);boolean more=rows.size()>limit;var page=more?rows.subList(0,limit):rows;
  return new ExportDataPage(page.stream().map(this::values).toList(),page.isEmpty()?null:page.get(page.size()-1).id(),more);
 }
 private Map<String,Object> values(StudentTaskExportRow row){
  String source=switch(row.sourceType()){case "FAMILY"->"家庭";case "TEACHER"->"教师";case "ORGANIZATION"->"机构";default->throw new IllegalStateException("未知任务来源");};
  String status=switch(row.status()){case "PENDING_CLAIM"->"待认领";case "IN_PROGRESS"->"进行中";case "PENDING_REVIEW"->"待审核";case "NEEDS_IMPROVEMENT"->"待优化";case "EXEMPT"->"免执行";case "COMPLETED"->"已完成";case "INVALIDATED"->"已失效";default->throw new IllegalStateException("未知任务状态");};
  String review=row.reviewStatus()==null?"未提交":switch(row.reviewStatus()){case "SUBMITTED"->"待审核";case "APPROVED"->"已通过";case "REJECTED"->"已驳回";default->throw new IllegalStateException("未知打卡审核状态");};
  return Map.of("STUDENT_NAME",ExportMasking.familyName(row.studentName()),"TASK_TITLE",row.title(),"SOURCE_TYPE",source,"STATUS",status,"POINTS",row.points(),"REVIEW_STATUS",review);
 }
 private void requireScope(ExportRequestDefinition r){if(r==null||r.studentTaskAssignmentIds()==null)throw new IllegalArgumentException("任务报表缺少冻结范围");}
}
