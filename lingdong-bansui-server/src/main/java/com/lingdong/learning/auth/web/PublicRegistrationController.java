package com.lingdong.learning.auth.web;
import com.lingdong.learning.auth.application.*;
import com.lingdong.learning.auth.domain.AuthClientType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
@RestController
@RequestMapping("/api/v1")
public class PublicRegistrationController {
 private final PublicRegistrationService service;private final RegistrationVerificationService codes;private final RegistrationDelivery delivery;
 public PublicRegistrationController(PublicRegistrationService service,RegistrationVerificationService codes,RegistrationDelivery delivery){this.service=service;this.codes=codes;this.delivery=delivery;}
 public record Context(boolean emailEnabled,boolean smsEnabled,String agreementVersion,int codeExpiresInSeconds,int retryAfterSeconds){}
 public record Agreement(String version,String content){}
 public record CodeRequest(String channel,String destination,AuthClientType clientType){}
 public record CodeResponse(LocalDateTime expiresAt,int retryAfterSeconds){}
 public record RegistrationResponse(AuthenticatedSession session){}
 @GetMapping("/public/registration/context") public Context context(){return new Context(delivery.emailEnabled(),delivery.smsEnabled(),service.version(),300,60);}
 @GetMapping("/public/registration/agreement") public Agreement agreement(){return new Agreement(service.version(),service.content());}
 @PostMapping("/auth/registration-codes") public CodeResponse send(@RequestBody CodeRequest c,HttpServletRequest request){if(c.clientType()!=AuthClientType.WEB&&c.clientType()!=AuthClientType.MINIAPP)throw new IllegalArgumentException("客户端无效");codes.send(c.channel(),RegistrationVerificationService.normalize(c.channel(),c.destination()),request.getRemoteAddr());return new CodeResponse(LocalDateTime.now().plusMinutes(5),60);}
 @PostMapping("/auth/registrations") public RegistrationResponse register(@RequestBody PublicRegistrationService.Command c,HttpServletRequest request){return new RegistrationResponse(service.register(c,request.getRemoteAddr()));}
}
