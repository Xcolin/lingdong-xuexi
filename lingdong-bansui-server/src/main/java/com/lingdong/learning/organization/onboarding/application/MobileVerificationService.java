package com.lingdong.learning.organization.onboarding.application;
import com.lingdong.learning.organization.onboarding.infrastructure.persistence.MobileVerificationMapper;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class MobileVerificationService {
 private final UserMapper users;private final MobileVerificationMapper proofs;private final ParentSmsVerificationService sms;
 public MobileVerificationService(UserMapper users,MobileVerificationMapper proofs,ParentSmsVerificationService sms){this.users=users;this.proofs=proofs;this.sms=sms;}
 private User actor(Long id){var user=users.findByIdForUpdate(id);if(user==null||user.status()!=UserStatus.ENABLED||user.type()==UserType.STUDENT)throw new IllegalArgumentException("需要有效非学生账号");if(user.mobile()==null||user.mobile().isBlank())throw new IllegalArgumentException("请先在账号资料中填写手机号");return user;}
 @Transactional public IssuedParentSmsCode issue(Long id,AuthClientType client){var user=actor(id);return sms.issue(user.mobile(),ParentSmsPurpose.ORGANIZATION_ONBOARDING,client,"onboarding-user:"+id);}
 @Transactional public void verify(Long id,AuthClientType client,String code){var user=actor(id);sms.verifyAndConsume(user.mobile(),ParentSmsPurpose.ORGANIZATION_ONBOARDING,client,code);proofs.save(id,user.mobile());}
}
