package com.lingdong.learning.student.application.transfer;

import com.lingdong.learning.student.infrastructure.persistence.*;
import com.lingdong.learning.student.domain.StudentStatus;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/** One approved proposal per transaction. No destination enrollment is created here. */
@Service
public class TransferApplicationExecutor {
 public enum Result {SKIPPED,BLOCKED,COMPLETED}
 private final TransferApplicationRepository repository;private final TransferApplicationAccess access;
 private final StudentMapper students;private final StudentOrganizationMapper relationships;private final StudentOrganizationChangeMapper history;
 private final IdGenerator ids;private final Clock clock;private final FeatureAccessService features;
 public TransferApplicationExecutor(TransferApplicationRepository repository,TransferApplicationAccess access,StudentMapper students,StudentOrganizationMapper relationships,StudentOrganizationChangeMapper history,IdGenerator ids,Clock clock,FeatureAccessService features){this.repository=repository;this.access=access;this.students=students;this.relationships=relationships;this.history=history;this.ids=ids;this.clock=clock;this.features=features;}
 @Transactional public Result execute(long id){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  var locked=repository.lockApplication(id);if(locked.isEmpty())return Result.SKIPPED;
  var stored=locked.get();var a=stored.application();var now=LocalDateTime.now(clock);
  if(a.status()!=TransferApplication.Status.APPROVED||a.effectiveDate().isAfter(now.toLocalDate())||repository.executionBlocked(id))return Result.SKIPPED;
  var student=students.findByIdForUpdate(a.studentId());var identity=repository.executionIdentity(id);
  String issue=student==null||student.status()!=StudentStatus.ENABLED?"STUDENT_UNAVAILABLE":access.executionIssue(stored,identity);
  if(issue!=null){if(!repository.blockExecution(id,stored.version(),issue))throw new IllegalStateException("转学申请执行状态冲突");return Result.BLOCKED;}
  relationships.deactivateActiveClassesInEnrollment(a.studentId(),a.sourceInstitutionId());
  relationships.removeInstitutionMemberships(student.studentUserId(),a.sourceInstitutionId());
  if(relationships.deactivateActiveEnrollment(a.studentId(),a.sourceInstitutionId())!=1)throw new IllegalStateException("入学关系已变更");
  if(history.insert(ids.nextId(),a.studentId(),"ENROLLMENT_DEACTIVATE",a.sourceInstitutionId(),null,a.reason(),identity.reviewerId(),now)!=1)throw new IllegalStateException("转学执行记录保存失败");
  a.complete(now);
  if(!repository.completeExecution(id,stored.version(),now))throw new IllegalStateException("转学申请完成状态冲突");
  return Result.COMPLETED;
 }
}
