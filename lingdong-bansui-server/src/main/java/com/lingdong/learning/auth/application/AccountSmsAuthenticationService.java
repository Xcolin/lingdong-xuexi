package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verified phone login is separate from registration, roles and agreement acceptance. */
@Service
public class AccountSmsAuthenticationService {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ParentSmsVerificationService codes;
    private final AuthenticationApplicationService auth;
    private final UserMapper users;
    private final ParentPhoneAuthenticationService parents;
    private final FeatureAccessService features;
    public AccountSmsAuthenticationService(ParentSmsVerificationService codes,AuthenticationApplicationService auth,UserMapper users,ParentPhoneAuthenticationService parents,FeatureAccessService features){this.codes=codes;this.auth=auth;this.users=users;this.parents=parents;this.features=features;}
    public record Command(@NotBlank @Pattern(regexp="1[3-9][0-9]{9}") String mobile,
        @NotBlank @Pattern(regexp="[0-9]{6}") String code,@NotNull AuthClientType clientType,
        @NotBlank @Size(max=128) String deviceId,@NotBlank @Size(max=100) String deviceName){}
    public record Result(AuthenticatedSession session,boolean onboardingRequired,boolean agreementAcceptanceRequired,String currentAgreementVersion){}
    private void requireClient(AuthClientType client){if(client!=AuthClientType.WEB&&client!=AuthClientType.MINIAPP)throw new IllegalArgumentException("客户端无效");features.requireEnabled("PARENT_PHONE_AUTH",null);}
    public IssuedParentSmsCode issue(String mobile,AuthClientType client,String source){requireClient(client);return codes.issue(mobile,ParentSmsPurpose.ACCOUNT_LOGIN,client,source);}
    @Transactional
    public Result login(Command command){
        requireClient(command.clientType());
        codes.verifyAndConsume(command.mobile(),ParentSmsPurpose.ACCOUNT_LOGIN,command.clientType(),command.code());
        var user=users.findByMobileForUpdate(command.mobile());
        if(user==null)throw new IllegalArgumentException("手机号尚未注册，请先注册账号");
        if(jdbc!=null && jdbc.queryForObject("select count(*) from auth_verified_mobile where user_id=? and mobile=?",Long.class,user.id(),command.mobile())!=1L)throw new AuthenticationFailedException();
        var session=auth.loginExistingAccountByMobile(command.mobile(),command.clientType(),command.deviceId(),command.deviceName());
        var state=parents.getParentAccessState(user.id());
        return new Result(session,state!=null&&state.onboardingRequired(),state!=null&&state.agreementAcceptanceRequired(),state==null?"":state.currentAgreementVersion());
    }
}

