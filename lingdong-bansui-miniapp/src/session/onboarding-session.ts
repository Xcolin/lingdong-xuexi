import { getRegistrationSession, resolveRegistrationSession } from './registration-session';
import { getParentSession } from './parent-session';
import { getOrganizationSession } from './organization-session';

export type OnboardingIdentity = 'parent' | 'organization' | 'personal';

/** Every onboarding page carries its source identity; never fall back to another account. */
export function onboardingIdentity(value: unknown): OnboardingIdentity | null {
  return value === 'parent' || value === 'organization' || value === 'personal' ? value : null;
}

export function getOnboardingSession(identity: OnboardingIdentity | null) {
  if (identity === 'parent') return getParentSession();
  if (identity === 'organization') return getOrganizationSession();
  if (identity === 'personal') return getRegistrationSession();
  return null;
}

export function onboardingUrl(page: string, identity: OnboardingIdentity, query = ''): string {
  return `/pages/${page}/${page}?identity=${identity}${query}`;
}

export async function resolveOnboardingSession(identity:OnboardingIdentity|null){return identity==='personal'?resolveRegistrationSession():getOnboardingSession(identity);}
