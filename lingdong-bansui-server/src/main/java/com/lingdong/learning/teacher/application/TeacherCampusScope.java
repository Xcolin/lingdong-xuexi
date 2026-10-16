package com.lingdong.learning.teacher.application;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.organization.application.OrganizationHierarchy;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.common.web.ResourceNotFoundException;
final class TeacherCampusScope {
 private TeacherCampusScope(){}
 static String requirePath(AuthenticatedUser actor,Long campusId,Long schoolId,OrganizationMapper organizations,OrganizationDataScopeService scopes){
  if(campusId==null)return null;
  var campus=organizations.findById(campusId);
  if(actor==null||campus==null||!"CAMPUS".equals(campus.typeCode())||!scopes.canAccess(actor.userId(),campusId))throw missing();
  var school=OrganizationHierarchy.schoolOf(campus,organizations);
  if(school==null||schoolId!=null&&!schoolId.equals(school.id()))throw missing();
  if(!OrganizationOperationalStatusService.isOperational(campus)||!OrganizationOperationalStatusService.isOperational(school))throw new IllegalStateException("校区或所属机构已停用");
  return campus.path();
 }
 private static ResourceNotFoundException missing(){return new ResourceNotFoundException("校区不存在或无权访问");}
}
