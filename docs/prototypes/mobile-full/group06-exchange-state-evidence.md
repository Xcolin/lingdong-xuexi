# Exchange state evidence (2026-10-13)

- 06-03: rewards selectedReward independent confirmation view. Entering and cancelling never call applyRewardExchange. Confirm applies existing request once; parent approval performs charge according to existing contract. No immediate charge wording.
- 06-04: rewards selectedExchange independent detail view. True server-returned exchange status, requestedAt, reviewedAt, verifiedAt and approvalDeadline are rendered; no delivery estimate, shipping, stock or invented reviewer name.
- 06-08: parent-rewards verification independent confirmation view. Primary guardian plus review permission and PENDING_VERIFICATION required. User must explicitly acknowledge delivery; confirmation reuses access and epoch/token validation, then invokes existing verify endpoint. Cancel sends no verification request.
- Student exchange submission uses createExchangeFlight and session revision guard. Hiding/unloading clears selected data. Old-session API response cannot populate new-session confirmation or detail.
- Automated verify-exchange-flight.cjs checks cancel/no request, concurrent double tap/single request, changed-session stale response ignored and recovery after request rejection.
- No real exchange, approval, verification or production credential mutation was performed. Screenshots/native visual acceptance remain outstanding.
