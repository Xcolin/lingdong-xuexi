package com.lingdong.learning.responsibility;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.student.application.StudentManagementAccessService;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ResponsibilityServiceTest {
    final ResponsibilityStore store=mock(ResponsibilityStore.class);
    final StudentManagementAccessService access=mock(StudentManagementAccessService.class);
    final PermissionDecisionService permissions=mock(PermissionDecisionService.class);
    final AuthenticatedUser actor=new AuthenticatedUser(1L,2L,"operator","操作人",AuthClientType.MINIAPP,List.of());
    final ResponsibilityService service=new ResponsibilityService(store,access,permissions);
    @BeforeEach void setup(){
        when(store.enrolled(10,20)).thenReturn(true);
        when(store.activeAdministrator(1,20)).thenReturn(true);
        when(access.organizationAllowed(actor,20L)).thenReturn(true);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"STUDENT_RESPONSIBILITY_READ")).thenReturn(true);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"STUDENT_RESPONSIBILITY_TRANSFER")).thenReturn(true);
        when(store.candidates(20)).thenReturn(List.of(new ResponsibilityStore.Person("1","操作人"),new ResponsibilityStore.Person("3","新老师")));
    }
    @Test void absentRowIsVersionZeroAndActorRemainsCandidate(){
        when(store.fallback(10,20)).thenReturn(new ResponsibilityStore.Fallback(new ResponsibilityStore.Person("2","班主任"),"一班"));
        assertThat(service.current(actor,10,20).versionNo()).isZero();
        assertThat(service.options(actor,10,20)).extracting(ResponsibilityStore.Person::id).contains("1","3");
    }
    @Test void unavailableAssignedDoesNotFallback(){
        when(store.row(10,20)).thenReturn(new ResponsibilityStore.Row(2L,4));
        when(store.person(2)).thenReturn(new ResponsibilityStore.Person("2","原老师"));
        var result=service.current(actor,10,20);
        assertThat(result.source()).isEqualTo("ASSIGNED");
        assertThat(result.availability()).isEqualTo("UNAVAILABLE");
        verify(store,never()).fallback(anyLong(),anyLong());
    }
    @Test void firstWriteRecordsZeroToOneAndAllowsActor(){
        service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,1L,"RESIGNATION_HANDOVER",null,0));
        verify(store).insert(10,20,1,1);
        verify(store).history(10,20,null,1,"RESIGNATION_HANDOVER",null,1,0,1);
    }
    @Test void staleVersionAndNoChangeProduceNoHistory(){
        when(store.row(10,20)).thenReturn(new ResponsibilityStore.Row(3L,2));
        when(store.person(3)).thenReturn(new ResponsibilityStore.Person("3","新老师"));
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,1L,"OTHER","原因",1))).isInstanceOf(ResponsibilityConflictException.class);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER","原因",2))).isInstanceOf(IllegalArgumentException.class);
        verify(store,never()).history(anyLong(),anyLong(),any(),anyLong(),anyString(),any(),anyLong(),anyInt(),anyInt());
    }
    @Test void guardianReadsOwnChildButCannotTransfer(){
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"STUDENT_RESPONSIBILITY_READ")).thenReturn(false);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"STUDENT_RESPONSIBILITY_TRANSFER")).thenReturn(false);
        when(permissions.isAllowed(1L,PermissionClient.MINIAPP,"PARENT_RELATIONSHIP_READ")).thenReturn(true);
        when(store.guardian(1,10)).thenReturn(true);
        assertThat(service.current(actor,10,20).source()).isEqualTo("NONE");
        assertThatThrownBy(()->service.options(actor,10,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
        when(store.guardian(1,10)).thenReturn(false);
        assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(SystemOperationAccessDeniedException.class);
    }
    @Test void crossInstitutionAndInvalidReasonRejected(){
        assertThatThrownBy(()->service.current(actor,10,21)).isInstanceOf(SystemOperationAccessDeniedException.class);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER"," ",0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"UNKNOWN",null,0))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void uniqueInsertRaceAndCasMissAreConflictsWithoutHistory(){
        doThrow(new org.springframework.dao.DuplicateKeyException("race")).when(store).insert(10,20,3,1);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"WORK_ADJUSTMENT",null,0))).isInstanceOf(ResponsibilityConflictException.class);
        when(store.row(10,20)).thenReturn(new ResponsibilityStore.Row(2L,1));
        when(store.update(10,20,3,1,1)).thenReturn(false);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"WORK_ADJUSTMENT",null,1))).isInstanceOf(ResponsibilityConflictException.class);
        verify(store,never()).history(anyLong(),anyLong(),any(),anyLong(),anyString(),any(),anyLong(),anyInt(),anyInt());
    }
    @Test void invalidTargetDisabledEnrollmentAndOversizedReasonRejected(){
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,99L,"WORK_ADJUSTMENT",null,0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.transfer(actor,10,new ResponsibilityService.TransferRequest(20L,3L,"OTHER","长".repeat(201),0))).isInstanceOf(IllegalArgumentException.class);
        when(store.enrolled(10,20)).thenReturn(false);
        assertThatThrownBy(()->service.current(actor,10,20)).isInstanceOf(com.lingdong.learning.common.web.ResourceNotFoundException.class);
    }
}
