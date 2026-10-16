package com.lingdong.learning.student.web;
import com.lingdong.learning.student.application.transfer.TransferApplicationRepository;
import com.lingdong.learning.student.infrastructure.persistence.StudentMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.attachment.domain.FileStatus;
import org.springframework.stereotype.Component;
/** Formats only results already authorized by the application service. */
@Component
public class TransferApplicationPresentation {
 private final StudentMapper students;private final OrganizationMapper organizations;private final FileRelationMapper relations;
 public TransferApplicationPresentation(StudentMapper students,OrganizationMapper organizations,FileRelationMapper relations){this.students=students;this.organizations=organizations;this.relations=relations;}
 public TransferApplicationController.Display display(TransferApplicationRepository.Stored stored){
  var application=stored.application();var student=students.findById(application.studentId());var institution=organizations.findById(application.sourceInstitutionId());
  var proofs=relations.findActiveFilesByBusiness("STUDENT_TRANSFER",stored.id(),"MATERIAL").stream().filter(f->application.attachmentIds().contains(f.id())&&f.status()==FileStatus.AVAILABLE&&"STUDENT_TRANSFER".equals(f.moduleCode())&&"MATERIAL".equals(f.fileCategory())).map(f->new TransferMaterialController.Uploaded(f.id().toString(),f.originalName(),f.contentType(),f.sizeBytes())).toList();
  return new TransferApplicationController.Display(student==null?null:student.studentName(),institution==null?null:institution.name(),stored.reviewReason(),stored.reviewedAt(),stored.createdAt(),application.completedAt(),proofs);
 }
}
