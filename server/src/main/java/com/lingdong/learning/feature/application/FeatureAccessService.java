package com.lingdong.learning.feature.application;
import com.lingdong.learning.feature.domain.FeatureStatus;
import com.lingdong.learning.feature.domain.FeatureToggle;
import com.lingdong.learning.feature.infrastructure.persistence.FeatureToggleMapper;
import org.springframework.stereotype.Service;
/** Applies the feature-toggle precedence before role or data authorization. */
@Service public class FeatureAccessService {
 private final FeatureToggleMapper mapper;
 public FeatureAccessService(FeatureToggleMapper mapper) { this.mapper=mapper; }
 public boolean isEnabled(String code, Long organizationId) {
  FeatureToggle global=mapper.findGlobal(code); if(global==null || global.status()==FeatureStatus.DISABLED) return false;
  if(organizationId==null) return true;
  FeatureToggle local=mapper.findOrganization(code,"ORG:"+organizationId);
  return local==null || local.status()==FeatureStatus.ENABLED;
 }
 public void requireEnabled(String code, Long organizationId) { if(!isEnabled(code,organizationId)) throw new FeatureDisabledException(code); }
}
