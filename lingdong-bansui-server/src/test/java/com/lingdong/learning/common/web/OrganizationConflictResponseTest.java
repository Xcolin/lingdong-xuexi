package com.lingdong.learning.common.web;
import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.organization.application.DuplicateOrganizationCodeException;
import com.lingdong.learning.organization.application.DuplicateOrganizationNameException;
import com.lingdong.learning.organization.application.OrganizationNotOperationalException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 组织业务冲突异常必须映射为 4xx 业务响应而不是 500 兜底（真实隔离HTTP曾返回500）。 */
class OrganizationConflictResponseTest {
 private final ApiExceptionHandler handler = new ApiExceptionHandler(new SecurityErrorResponseFactory(), mock(ErrorAlertService.class));
 private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/classes/campuses");

 @Test void duplicateNameIsConflictWithUserName() {
  var response = handler.handleDuplicateOrganizationName(new DuplicateOrganizationNameException("联调校区"), request);
  assertThat(response.getStatusCode().value()).isEqualTo(409);
  assertThat(response.getBody().code()).isEqualTo("ORGANIZATION_NAME_DUPLICATE");
  assertThat(response.getBody().message()).contains("联调校区");
 }

 @Test void duplicateCodeIsConflictWithoutLeakingInternal() {
  var response = handler.handleDuplicateOrganizationCode(new DuplicateOrganizationCodeException("CMP_123"), request);
  assertThat(response.getStatusCode().value()).isEqualTo(409);
  assertThat(response.getBody().code()).isEqualTo("ORGANIZATION_CODE_DUPLICATE");
  assertThat(response.getBody().message()).doesNotContain("CMP_123");
 }

 @Test void notOperationalIsConflictWithStableMessage() {
  var response = handler.handleOrganizationNotOperational(new OrganizationNotOperationalException(1791858814796002L), request);
  assertThat(response.getStatusCode().value()).isEqualTo(409);
  assertThat(response.getBody().code()).isEqualTo("ORGANIZATION_NOT_OPERATIONAL");
  assertThat(response.getBody().message()).doesNotContain("1791858814796002");
 }
}
