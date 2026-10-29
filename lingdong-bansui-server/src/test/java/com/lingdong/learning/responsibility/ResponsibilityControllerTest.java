package com.lingdong.learning.responsibility;

import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResponsibilityControllerTest {
    private static org.springframework.web.method.support.HandlerMethodArgumentResolver principal(){
        return principal(null);
    }
    private static org.springframework.web.method.support.HandlerMethodArgumentResolver principal(Object user){
        return new org.springframework.web.method.support.HandlerMethodArgumentResolver(){
            public boolean supportsParameter(org.springframework.core.MethodParameter parameter){return parameter.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);}
            public Object resolveArgument(org.springframework.core.MethodParameter parameter,org.springframework.web.method.support.ModelAndViewContainer container,org.springframework.web.context.request.NativeWebRequest request,org.springframework.web.bind.support.WebDataBinderFactory factory){return user;}
        };
    }
    @Test void conflictIs409AndDoesNotLeakSql() throws Exception {
        var service=mock(ResponsibilityService.class);
        when(service.transfer(any(),eq(10L),any())).thenThrow(new ResponsibilityConflictException());
        var mvc=MockMvcBuilders.standaloneSetup(new ResponsibilityController(service))
                .setCustomArgumentResolvers(principal())
                .setControllerAdvice(new ResponsibilityExceptionHandler(new SecurityErrorResponseFactory())).build();
        mvc.perform(post("/api/v1/students/10/institution-responsibility-transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionOrganizationId\":20,\"targetUserId\":3,\"reasonType\":\"WORK_ADJUSTMENT\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_RESPONSIBILITY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("负责人已变更，请刷新重试"));
    }
    @Test void invalidVersionAndLongReasonAre400BeforeService() throws Exception {
        var service=mock(ResponsibilityService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ResponsibilityController(service)).setCustomArgumentResolvers(principal()).build();
        mvc.perform(post("/api/v1/students/10/institution-responsibility-transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionOrganizationId\":20,\"targetUserId\":3,\"reasonType\":\"OTHER\",\"reasonText\":\"说明\",\"expectedVersion\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/students/10/institution-responsibility-transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionOrganizationId\":20,\"targetUserId\":3,\"reasonType\":\"OTHER\",\"reasonText\":\""+"长".repeat(201)+"\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void parentPostIsActuallyForbiddenAndPagingBoundsAre400() throws Exception {
        var store=mock(ResponsibilityStore.class);
        var access=mock(com.lingdong.learning.student.application.StudentManagementAccessService.class);
        var permissions=mock(com.lingdong.learning.permission.application.PermissionDecisionService.class);
        var user=new com.lingdong.learning.auth.application.AuthenticatedUser(1L,2L,"parent","家长",com.lingdong.learning.auth.domain.AuthClientType.MINIAPP,java.util.List.of());
        when(permissions.isAllowed(eq(1L),any(),eq("PARENT_RELATIONSHIP_READ"))).thenReturn(true);
        when(store.guardian(1,10)).thenReturn(true);when(store.enrolled(10,20)).thenReturn(true);
        var service=new ResponsibilityService(store,access,permissions);
        var mvc=MockMvcBuilders.standaloneSetup(new ResponsibilityController(service)).setCustomArgumentResolvers(principal(user))
                .setControllerAdvice(new com.lingdong.learning.common.web.ApiExceptionHandler(new SecurityErrorResponseFactory(),mock(com.lingdong.learning.common.alert.ErrorAlertService.class))).build();
        mvc.perform(post("/api/v1/students/10/institution-responsibility-transfer").contentType(MediaType.APPLICATION_JSON)
                .content("{\"institutionOrganizationId\":20,\"targetUserId\":3,\"reasonType\":\"OTHER\",\"reasonText\":\"接手\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/10/institution-responsibility-transfers").param("institutionOrganizationId","20").param("page","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/10/institution-responsibility-transfers").param("institutionOrganizationId","20").param("pageSize","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/10/institution-responsibility-transfers").param("institutionOrganizationId","20").param("pageSize","0")).andExpect(status().isBadRequest());
        verify(store,never()).historyPage(anyLong(),anyLong(),anyInt(),anyLong());
        verify(store,never()).insert(anyLong(),anyLong(),anyLong(),anyLong());
    }
    @Test void allPersonIdsRemainStringsBeyondJavascriptSafeIntegerAndHistoryDefaultsArePaged() throws Exception {
        var service=mock(ResponsibilityService.class);String id="9223372036854775806";
        when(service.current(any(),eq(10L),eq(20L))).thenReturn(new ResponsibilityService.Current(new ResponsibilityStore.Person(id,"老师"),"ASSIGNED","AVAILABLE",null,1));
        var entry=new ResponsibilityStore.Transfer(id,"原老师",id,"新老师","OTHER","说明",id,"操作人",0,1,java.time.LocalDateTime.of(2026,1,1,0,0));
        when(service.history(any(),eq(10L),eq(20L),eq(1),eq(20))).thenReturn(new ResponsibilityService.HistoryPage(java.util.List.of(entry),1,1,20));
        var mvc=MockMvcBuilders.standaloneSetup(new ResponsibilityController(service)).setCustomArgumentResolvers(principal()).build();
        mvc.perform(get("/api/v1/students/10/institution-responsibility").param("institutionOrganizationId","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responsibleUser.id").value(id)).andExpect(jsonPath("$.responsibleUser.id").isString());
        mvc.perform(get("/api/v1/students/10/institution-responsibility-transfers").param("institutionOrganizationId","20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].fromUserId").value(id)).andExpect(jsonPath("$.items[0].toUserId").value(id))
                .andExpect(jsonPath("$.items[0].transferredBy").value(id)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.total").value(1));
    }
}
