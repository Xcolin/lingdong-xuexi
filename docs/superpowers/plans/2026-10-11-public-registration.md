# Public registration implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review each task.

**Goal:** 默认邮箱验证注册；登录后没有我的组织入口；无组织账号使用入驻进度。

**Architecture:** 注册验证和账号创建作为独立公共认证流程，复用已有会话、账号状态、入驻事务；独立邮箱绑定持久化，增量迁移，不清理已有数据。

**Tech Stack:** Spring Boot/MyBatis/MySQL/Redis/SMTP, React/Ant Design, uni-app.

## Shared API contract

- GET /api/v1/public/registration/context -> {emailEnabled,smsEnabled,agreementVersion,codeExpiresInSeconds,retryAfterSeconds}
- POST /api/v1/auth/registration-codes {channel:EMAIL|SMS,destination,clientType:WEB|MINIAPP} -> {expiresAt,retryAfterSeconds}
- POST /api/v1/auth/registrations {channel,destination,code,username,displayName,password,passwordConfirmation,agreementAccepted,agreementVersion,clientType,deviceId,deviceName} -> {session: existing AuthenticatedSession structure}
- No public address-binding enumeration or password overwrites. Duplicate address returns friendly existing-account login message only after valid OTP.
- Existing authenticated onboarding context adds email, verifiedEmail; organization applications accept verified email OR mobile. Application result may include email in addition to mobile.

## Task 1: Backend

- [x] Write tests for validation, OTP consumption/rate limits, uniqueness, no implicit admin, neutral identity sessions, email-only institution onboarding.
- [x] Implement registration mail sender, configured-only SMS sender, verification store/service, controller and persistence migration.
- [x] Reuse account password policy and session creation; explicit current agreement acceptance stored atomically.
- [x] Integrate email contact proof into institution onboarding and protect old scope/role checks.
- [x] Run targeted tests; root performs isolated migration and real deployment.

## Task 2: Web

- [x] Registration page default email, code countdown, account/password/confirmation/protocol form.
- [x] Login page registration link; login destination selects onboarding guide, pending progress or existing authorized business page.
- [x] Remove my-organizations from ordinary navigation/header; registration-progress standalone page retains pending applications and creation.
- [x] Move owner-transfer access into organization management, retaining existing checks.
- [x] Email context accepted in institution form; regressions and production build.

## Task 3: Mobile

- [x] Public registration page and login links; email default, SMS when enabled.
- [x] Remove normal my-organizations buttons; retain pending admission page reached through registration/login.
- [x] Preserve session ownership and selected organization boundaries, new neutral account can obtain entry session.
- [x] Email-only institution submission; typecheck and H5/WeChat builds.

## Task 4: Integration and release

- [x] Spec and code review focused on uniqueness, authorization, privacy and redirects.
- [x] Backup existing real database; isolated Flyway and HTTP registration validation with controlled delivery capture, no seeded public passwords.
- [x] Build latest backend artifact; restart exact existing service only after checks; smoke test services and public context.
- [x] Record evidence and remaining real email/device limitations.
