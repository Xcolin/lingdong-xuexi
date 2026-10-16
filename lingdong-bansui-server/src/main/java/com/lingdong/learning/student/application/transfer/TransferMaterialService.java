package com.lingdong.learning.student.application.transfer;

import com.lingdong.learning.attachment.application.*;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.attachment.infrastructure.persistence.FileRelationMapper;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferMaterialService {
 private final TransferApplicationAccess access;private final TransferApplicationService applications;
 private final ManagedAttachmentContentService content;private final ManagedFileMapper files;private final FeatureAccessService features;private final FileRelationMapper relations;
 public TransferMaterialService(TransferApplicationAccess access,TransferApplicationService applications,ManagedAttachmentContentService content,ManagedFileMapper files,FeatureAccessService features,FileRelationMapper relations){this.access=access;this.applications=applications;this.content=content;this.files=files;this.features=features;this.relations=relations;}
 @Transactional public ManagedFile upload(AuthenticatedUser actor,long student,long source,long family,String name,String mime,byte[] bytes){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);features.requireEnabled("ATTACHMENT_SERVICE",null);
  access.requireFamily(actor,student,source,family,"STUDENT_TRANSFER_REQUEST_CREATE");
  String detected=TransferMaterialFormat.detect(name,mime,bytes);
  return content.store(actor.userId(),"STUDENT_TRANSFER","MATERIAL",name,detected,bytes);
 }
 @Transactional public ManagedFile uploadInstitution(AuthenticatedUser actor,long student,long source,String name,String mime,byte[] bytes){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);features.requireEnabled("ATTACHMENT_SERVICE",null);
  applications.institutionContext(actor,student,source);
  String detected=TransferMaterialFormat.detect(name,mime,bytes);
  return content.store(actor.userId(),"STUDENT_TRANSFER","MATERIAL",name,detected,bytes);
 }
 @Transactional(readOnly=true) public AttachmentContentView read(AuthenticatedUser actor,long applicationId,long fileId){
  features.requireEnabled("ATTACHMENT_SERVICE",null);
  var application=applications.read(actor,applicationId);
  if(!application.application().attachmentIds().contains(fileId))throw new ResourceNotFoundException("转学材料不存在或不可访问");
  if(relations.findActiveFilesByBusiness("STUDENT_TRANSFER",applicationId,"MATERIAL").stream().noneMatch(f->f.id().longValue()==fileId))throw new ResourceNotFoundException("转学材料不存在或不可访问");
  var file=files.findById(fileId);
  if(file==null||file.status()!=FileStatus.AVAILABLE||!"STUDENT_TRANSFER".equals(file.moduleCode())||!"MATERIAL".equals(file.fileCategory()))throw new ResourceNotFoundException("转学材料不存在或不可访问");
  return content.read(fileId);
 }
 public void retire(AuthenticatedUser actor,long fileId){applications.retireMaterial(actor,fileId);}
}
