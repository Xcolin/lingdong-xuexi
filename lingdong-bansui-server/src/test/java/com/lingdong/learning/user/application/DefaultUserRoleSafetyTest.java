package com.lingdong.learning.user.application;
import com.lingdong.learning.user.domain.UserType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DefaultUserRoleSafetyTest {
 @Test void platformDefaultsToOrdinarySelfRoleAndCannotRequestPrivilegedDefault(){assertThat(UserAccessApplicationService.defaultRoleCode(UserType.PLATFORM,null)).isEqualTo("PLATFORM_USER");assertThatThrownBy(()->UserAccessApplicationService.defaultRoleCode(UserType.PLATFORM,"SYS_ADMIN")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->UserAccessApplicationService.defaultRoleCode(UserType.PLATFORM,"SYS_AUDITOR")).isInstanceOf(IllegalArgumentException.class);}
}
