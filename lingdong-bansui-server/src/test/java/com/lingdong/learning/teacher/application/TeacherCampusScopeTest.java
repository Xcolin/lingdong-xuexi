package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import java.util.List;
class TeacherCampusScopeTest {
 final OrganizationMapper orgs=mock(OrganizationMapper.class);final OrganizationDataScopeService scope=mock(OrganizationDataScopeService.class);final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"actor","actor",AuthClientType.MINIAPP,List.of());
 TeacherCampusScopeTest(){when(orgs.findById(2L)).thenReturn(Organization.create(2L,null,"ROOT","S","school","SCHOOL","/2/",1,null));when(orgs.findById(3L)).thenReturn(Organization.create(3L,2L,"S","C","campus","CAMPUS","/2/3/",1,null));when(scope.canAccess(1L,3L)).thenReturn(true);}
 @Test void actualCampusReturnsScopedPath(){assertThat(TeacherCampusScope.requirePath(actor,3L,2L,orgs,scope)).isEqualTo("/2/3/");}
 @Test void missingFilterDoesNotReadResources(){assertThat(TeacherCampusScope.requirePath(actor,null,null,orgs,scope)).isNull();verifyNoInteractions(orgs,scope);}
 @Test void outsideCampusIsNotAQueryContext(){when(scope.canAccess(1L,3L)).thenReturn(false);assertThatThrownBy(()->TeacherCampusScope.requirePath(actor,3L,2L,orgs,scope)).isInstanceOf(ResourceNotFoundException.class);}
 @Test void campusCannotBeCombinedWithForeignSchool(){assertThatThrownBy(()->TeacherCampusScope.requirePath(actor,3L,9L,orgs,scope)).isInstanceOf(ResourceNotFoundException.class);}
 @Test void schoolCannotMasqueradeAsCampus(){assertThatThrownBy(()->TeacherCampusScope.requirePath(actor,2L,2L,orgs,scope)).isInstanceOf(ResourceNotFoundException.class);}
 @Test void disabledCampusIsRejectedBeforeDirectoryQuery(){var disabled=mock(Organization.class);when(disabled.id()).thenReturn(3L);when(disabled.parentId()).thenReturn(2L);when(disabled.typeCode()).thenReturn("CAMPUS");when(disabled.status()).thenReturn(com.lingdong.learning.organization.domain.OrganizationStatus.DISABLED);when(disabled.effectiveStatus()).thenReturn(com.lingdong.learning.organization.domain.OrganizationEffectiveStatus.ENABLED);when(orgs.findById(3L)).thenReturn(disabled);assertThatThrownBy(()->TeacherCampusScope.requirePath(actor,3L,2L,orgs,scope)).isInstanceOf(IllegalStateException.class);}
}
