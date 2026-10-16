# Group 01 implementation evidence (2026-10-13)

No screenshot acceptance is claimed. Source references: reference/01-01.png through 01-10.png.

| Screen | Route / view state | Actual behavior retained | Remaining visual/data gap |
|---|---|---|---|
| 01-01 | unified-login; WECHAT without bindingTicket | WeChat partner entry, consent, registration, child entry | Original portrait is not extracted; approved WeChat differs from password reference |
| 01-02 | student-login ACCOUNT / QR / WECHAT | Account plus 4-digit login code, scanner, capability checks | No original portrait asset; scan/WeChat are approved additions |
| 01-03 | public-registration step 1 / 2 / 3 | Email default, optional enabled SMS, countdown, code verification on submission, passwords and consent | Three client stages now implemented: account information, email/SMS code, review/consent. Verification remains atomic with server registration; school/region fields are absent from registration DTO |
| 01-04 | unified-login bindingTicket + empty bindingChoice (selection) | Restart WeChat authorization, existing-account binding, explicit registration | Independent selection view followed by email or password panel; ticket stays out of URL |
| 01-05 | unified-login bindingTicket EMAIL + bindingChoice EMAIL | Real email verification and binding ticket | Independent form state on login route; ticket stays out of URL |
| 01-06 | unified-login bindingTicket PASSWORD + bindingChoice PASSWORD | Existing account/password plus optional binding email/code | Independent form state on login route; ticket stays out of URL |
| 01-07 | organization-onboarding type=family | Family name, real regional catalog, create API | No student name/grade fields in family-create DTO; no fake three-stage completion |
| 01-08 | organization-onboarding institution | School/training type, name, region, contact and verified email/mobile | Province/city split and job picker unavailable in current DTO |
| 01-09 | organization-application PENDING/REJECTED/WITHDRAWN/APPROVED | Actual timestamps, rejection reason, withdrawal, resubmission, enter organizations | No fabricated audit dates or estimated deadlines |
| 01-10 | my-organizations choose=true | Actual organization list and role, family/institution switch, invitations, applications | Preserve permitted cross-family/institution switch; no fake current organization selection |

Verification: page templates and all existing script actions preserved. Credential-changing operations were not exercised. Type-check/build results are reported separately by the executing agent.
