package com.lingdong.learning.student.application.transfer;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.attachment.infrastructure.persistence.ManagedFileMapper;
import com.lingdong.learning.attachment.domain.FileStatus;
import com.lingdong.learning.attachment.application.AttachmentFileApplicationService;
import com.lingdong.learning.attachment.application.AttachFileToBusinessCommand;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Proposal operations only; no operation here changes enrollment. */
@Service
public class TransferApplicationService {
 private final TransferApplicationRepository repository;private final TransferApplicationAccess access;
 private final ManagedFileMapper files;private final AttachmentFileApplicationService attachments;
 private final IdGenerator ids;private final Clock clock;private final FeatureAccessService features;
 public TransferApplicationService(TransferApplicationRepository r,TransferApplicationAccess a,ManagedFileMapper f,AttachmentFileApplicationService b,IdGenerator i,Clock c,FeatureAccessService switches){repository=r;access=a;files=f;attachments=b;ids=i;clock=c;features=switches;}
 @Transactional(readOnly=true) public TransferApplicationRepository.Stored read(AuthenticatedUser actor,long id){features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);access.requireViewPermission(actor);var stored=require(id);access.requireRead(actor,stored);return stored;}
 @Transactional(readOnly=true) public TransferApplicationRepository.Page list(AuthenticatedUser actor,long organizationId,String status,int page,int pageSize){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  boolean family=access.requireList(actor,organizationId);
  return repository.list(actor.userId(),organizationId,family,status,page,pageSize);
 }
 @Transactional public TransferApplicationRepository.Stored submit(AuthenticatedUser actor,long student,long source,long family,String target,String reason,LocalDate date,List<Long> fileIds){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  access.requireFamily(actor,student,source,family,"STUDENT_TRANSFER_REQUEST_CREATE");
  return persist(actor,student,source,family,target,reason,date,fileIds,null);
 }
 @Transactional public TransferApplicationRepository.Stored submitInstitution(AuthenticatedUser actor,long student,long source,String target,String reason,LocalDate date,List<Long> fileIds){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);long guardian=access.requireInstitutionSubmission(actor,student,source);
  long family=repository.uniqueGuardianFamily(student,guardian);
  return persist(actor,student,source,family,target,reason,date,fileIds,guardian);
 }
 @Transactional(readOnly=true) public long institutionContext(AuthenticatedUser actor,long student,long source){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);
  long guardian=access.requireInstitutionOptions(actor,student,source);
  return repository.uniqueGuardianFamily(student,guardian);
 }
 private TransferApplicationRepository.Stored persist(AuthenticatedUser actor,long student,long source,long family,String target,String reason,LocalDate date,List<Long> fileIds,Long guardian){
  var proposal=TransferApplication.submit(student,source,actor.userId(),target,reason,date,fileIds,LocalDate.now(clock));
  for(long id:proposal.attachmentIds()){var file=files.findById(id);if(file==null||file.status()!=FileStatus.AVAILABLE||!actor.userId().equals(file.uploaderId())||!"STUDENT_TRANSFER".equals(file.moduleCode())||!"MATERIAL".equals(file.fileCategory()))throw new SystemOperationAccessDeniedException("转学材料不存在或无权使用");}
  repository.lockUnusedMaterials(actor.userId(),proposal.attachmentIds());
  long id=ids.nextId();repository.insert(id,family,proposal);
  if(guardian==null)repository.recordApplicantClient(id,actor.clientType().name());else repository.recordInstitutionSubmission(id,guardian,actor.clientType().name());
  for(long file:proposal.attachmentIds())attachments.attachToBusiness(new AttachFileToBusinessCommand(file,"STUDENT_TRANSFER",id,"MATERIAL","TRANSFER_PARTICIPANTS"));
  return repository.find(id).orElseThrow();
 }
 @Transactional public TransferApplicationRepository.Stored review(AuthenticatedUser actor,long id,int version,boolean approved,String reason){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);var stored=require(id);access.requireReviewer(actor,stored);
  stored.application().review(actor.userId(),stored.application().sourceInstitutionId(),approved);
  if(!repository.review(id,version,actor.userId(),approved,reason,actor.clientType().name()))throw new IllegalStateException("申请已变更，请刷新后重试");return require(id);
 }
 @Transactional public TransferApplicationRepository.Stored withdraw(AuthenticatedUser actor,long id,int version){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);var stored=require(id);var a=stored.application();var identity=repository.executionIdentity(id);
  if("INSTITUTION".equals(identity.submissionMode()))access.requireInstitutionSubmission(actor,a.studentId(),a.sourceInstitutionId());else access.requireFamily(actor,a.studentId(),a.sourceInstitutionId(),stored.familyId(),"STUDENT_TRANSFER_REQUEST_CREATE");a.withdraw(actor.userId());
  if(!repository.withdraw(id,version,actor.userId()))throw new IllegalStateException("申请已变更，请刷新后重试");return require(id);
 }
 private TransferApplicationRepository.Stored require(long id){return repository.find(id).orElseThrow(()->new IllegalArgumentException("申请不存在或不可访问"));}
 @Transactional public TransferApplicationRepository.Stored rejectBlocked(AuthenticatedUser actor,long id,int version,String reason){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);var stored=require(id);access.requireBlockedReviewer(actor,stored);
  if(!repository.rejectBlocked(id,version,actor.userId(),reason,actor.clientType().name()))throw new IllegalStateException("申请已变更或不处于挂起状态");
  return require(id);
 }
 @Transactional public void retireMaterial(AuthenticatedUser actor,long fileId){
  features.requireEnabled("STUDENT_ORGANIZATION_RELATIONSHIP",null);features.requireEnabled("ATTACHMENT_SERVICE",null);access.requireCreatePermission(actor);
  repository.retireUnusedMaterial(actor.userId(),fileId);
 }
}
