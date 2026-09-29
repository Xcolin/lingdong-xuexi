package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.auth.infrastructure.persistence.ParentWechatBindingMapper;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.feature.application.FeatureAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 处理家长微信快捷登录和首次手机号绑定，绝不创建无手机号家长。 */
@Service
public class ParentWechatAuthenticationService {
    private static final String FEATURE_CODE = "PARENT_WECHAT_AUTH";

    private final ParentWechatIdentityGateway identityGateway;
    private final ParentWechatBindingTicketService ticketService;
    private final ParentWechatBindingMapper bindingMapper;
    private final ParentPhoneAuthenticationService phoneService;
    private final AuthenticationApplicationService sessionService;
    private final FeatureAccessService featureAccessService;
    private final ParentWechatIntegrationAccess integrationAccess;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ParentWechatAuthenticationService(
            ParentWechatIdentityGateway identityGateway,
            ParentWechatBindingTicketService ticketService,
            ParentWechatBindingMapper bindingMapper,
            ParentPhoneAuthenticationService phoneService,
            AuthenticationApplicationService sessionService,
            FeatureAccessService featureAccessService,
            ParentWechatIntegrationAccess integrationAccess,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.identityGateway = identityGateway;
        this.ticketService = ticketService;
        this.bindingMapper = bindingMapper;
        this.phoneService = phoneService;
        this.sessionService = sessionService;
        this.featureAccessService = featureAccessService;
        this.integrationAccess = integrationAccess;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public ParentWechatSessionExchange exchange(ParentWechatSessionCommand command) {
        Objects.requireNonNull(command, "微信登录请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        integrationAccess.requireAvailable();
        String temporaryCode = requiredText(command.temporaryCode(), "微信临时凭证", 128);
        String deviceId = requiredText(command.deviceId(), "设备标识", 128);
        String deviceName = requiredText(command.deviceName(), "设备名称", 100);

        ParentWechatIdentity identity = identityGateway.exchange(temporaryCode);
        ParentWechatBinding binding = bindingMapper.findActiveByIdentity(identity.appId(), identity.openId());
        if (binding == null) {
            return ParentWechatSessionExchange.bindingRequired(ticketService.issue(identity));
        }

        ParentAuthState state = phoneService.getParentState(binding.userId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (bindingMapper.touchLastLogin(binding.id(), now) != 1) {
            throw new AuthenticationFailedException();
        }
        AuthenticatedSession session = sessionService.createSession(
                binding.userId(), AuthClientType.MINIAPP, deviceId, deviceName);
        return ParentWechatSessionExchange.authenticated(new ParentPhoneAuthenticatedSession(
                session, state.onboardingRequired(), state.agreementAcceptanceRequired(),
                state.currentAgreementVersion()));
    }

    @Transactional
    public ParentPhoneAuthenticatedSession bind(ParentWechatBindingCommand command) {
        Objects.requireNonNull(command, "微信绑定请求不能为空");
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        integrationAccess.requireAvailable();
        String deviceId = requiredText(command.deviceId(), "设备标识", 128);
        String deviceName = requiredText(command.deviceName(), "设备名称", 100);
        ParentWechatIdentity identity = ticketService.consume(
                requiredText(command.bindingTicket(), "微信绑定凭证", 256));

        VerifiedParentAccount parent = phoneService.verifyWechatBindingMobile(
                new ParentWechatMobileBindingCommand(
                        command.mobile(), command.smsCode(), deviceId, deviceName,
                        command.agreementAccepted(), command.agreementVersion(), command.sourceAddressHash()));
        if (bindingMapper.findByUserId(parent.userId()) != null
                || bindingMapper.findActiveByIdentity(identity.appId(), identity.openId()) != null) {
            throw new AuthenticationFailedException();
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ParentWechatBinding binding = new ParentWechatBinding(
                idGenerator.nextId(), parent.userId(), identity.appId(), identity.openId(), identity.unionId(),
                ParentWechatBindingStatus.ACTIVE, now, now);
        try {
            if (bindingMapper.insert(binding) != 1) {
                throw new AuthenticationFailedException();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new AuthenticationFailedException();
        }
        AuthenticatedSession session = sessionService.createSession(
                parent.userId(), AuthClientType.MINIAPP, deviceId, deviceName);
        return new ParentPhoneAuthenticatedSession(
                session, parent.onboardingRequired(), false, parent.currentAgreementVersion());
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
