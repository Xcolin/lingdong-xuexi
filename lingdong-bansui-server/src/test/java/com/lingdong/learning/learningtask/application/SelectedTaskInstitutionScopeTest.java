package com.lingdong.learning.learningtask.application;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.common.security.SelectedOrganizationContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SelectedTaskInstitutionScopeTest {
 @Test void selectedCampusIncludesSiblingClassesInSameSchoolButNotOtherSchool() {
  var mapper=mock(OrganizationMapper.class);
  var school=Organization.create(1L,null,"ROOT","S","School","SCHOOL","/1/",0,null);
  var campus=Organization.create(2L,1L,"1","C","Campus","CAMPUS","/1/2/",0,null);
  when(mapper.findById(1L)).thenReturn(school);
  var request=new MockHttpServletRequest();request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,campus);
  RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  try {
   assertTrue(SelectedTaskInstitutionScope.allows(mapper,Organization.create(3L,1L,"1","A","Class","CLASS","/1/3/",0,null)));
   assertFalse(SelectedTaskInstitutionScope.allows(mapper,Organization.create(4L,null,"ROOT","B","Other","SCHOOL","/4/",0,null)));
  } finally {RequestContextHolder.resetRequestAttributes();}
 }
 @Test void noSelectionPreservesExistingAuthorizationBehavior(){RequestContextHolder.resetRequestAttributes();assertTrue(SelectedTaskInstitutionScope.allows(mock(OrganizationMapper.class),null));}
}
