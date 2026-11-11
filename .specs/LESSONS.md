# LESSONS - auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation - do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 - Assert Pageable sort field and direction in controller tests instead of matching Pageable with any
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `web-controllers` · harmful: 0
- features: fluxo-laboratorio-notificacoes
- evidence: NotificationController.java:30 (Mutant 4) (web-controllers)
- last seen: 2026-11-11T21:03:56Z

### L-002 - Test every boolean OR-branch guard independently in service unit tests
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `service-layer` · harmful: 0
- features: fluxo-laboratorio-notificacoes
- evidence: ExamOrderService.java:141 (Mutant 5) (service-layer)
- last seen: 2026-11-11T21:03:56Z

### L-003 - Verify enumerated invalid states and service not-found exceptions directly on each new endpoint and service method
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `exam-order` · harmful: 0
- features: fluxo-laboratorio-notificacoes
- evidence: FLOW-02 AC 2.1, AC 2.2, AC 2.3 (exam-order)
- last seen: 2026-11-11T21:03:56Z

## Quarantined (failed when applied - ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
