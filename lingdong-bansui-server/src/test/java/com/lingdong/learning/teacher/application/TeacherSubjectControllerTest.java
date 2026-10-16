package com.lingdong.learning.teacher.application;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TeacherSubjectControllerTest {
 @Test void realHttpMappingValidatesVersionAndDelegatesExactIdentifiers() throws Exception {
  var service=org.mockito.Mockito.mock(TeacherSubjectService.class);
  var actor=new com.lingdong.learning.auth.application.AuthenticatedUser(1L,2L,"actor","actor",com.lingdong.learning.auth.domain.AuthClientType.MINIAPP,java.util.List.of());
  org.mockito.Mockito.when(service.update(actor,2110871757849100288L,2110871760814473216L,null,2)).thenReturn(new com.lingdong.learning.teacher.infrastructure.persistence.TeacherSubjectStore.Metadata(2110871757849100288L,2110871760814473216L,null,3));
  var resolver=new org.springframework.web.method.support.HandlerMethodArgumentResolver(){
   public boolean supportsParameter(org.springframework.core.MethodParameter p){return p.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);}
   public Object resolveArgument(org.springframework.core.MethodParameter p,org.springframework.web.method.support.ModelAndViewContainer m,org.springframework.web.context.request.NativeWebRequest r,org.springframework.web.bind.support.WebDataBinderFactory b){return actor;}
  };
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.lingdong.learning.teacher.web.TeacherSubjectController(service)).setCustomArgumentResolvers(resolver).build();
  String url="/api/v1/organization-teachers/2110871757849100288/institutions/2110871760814473216/subject";
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url).contentType("application/json").content("{\"subjectCode\":null}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
  org.mockito.Mockito.verifyNoInteractions(service);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url).contentType("application/json").content("{\"subjectCode\":null,\"versionNo\":2}")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.teacherUserId").value("2110871757849100288")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.versionNo").value(3));
  org.mockito.Mockito.verify(service).update(actor,2110871757849100288L,2110871760814473216L,null,2);
 }
 @Test void responseKeepsLargeIdentifiersAsStrings() throws Exception {
  var response=Class.forName("com.lingdong.learning.teacher.web.TeacherSubjectController$SubjectResponse");
  var value=response.getConstructors()[0].newInstance(2110871757849100288L,2110871760814473216L,"MATH",3);
  var json=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(value);
  assertThat(json.path("teacherUserId").asText()).isEqualTo("2110871757849100288");assertThat(json.path("teacherUserId").isTextual()).isTrue();
  assertThat(json.path("institutionOrganizationId").isTextual()).isTrue();assertThat(json.path("versionNo").asInt()).isEqualTo(3);
 }
 @Test void updateRequiresExplicitNonnegativeVersionAndOriginalPermission() throws Exception {
  var controller=Class.forName("com.lingdong.learning.teacher.web.TeacherSubjectController");
  var request=Class.forName("com.lingdong.learning.teacher.web.TeacherSubjectController$UpdateSubjectRequest");
  try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();
   assertThat(validator.validate(request.getConstructors()[0].newInstance(null,null))).isNotEmpty();
   assertThat(validator.validate(request.getConstructors()[0].newInstance("MATH",-1))).isNotEmpty();
   assertThat(validator.validate(request.getConstructors()[0].newInstance(null,0))).isEmpty();
  }
  var method=java.util.Arrays.stream(controller.getDeclaredMethods()).filter(m->m.getName().equals("update")).findFirst().orElseThrow();
  assertThat(method.getAnnotation(com.lingdong.learning.common.security.RequirePermission.class).value()).isEqualTo("TEACHER_UPDATE");
 }
}
