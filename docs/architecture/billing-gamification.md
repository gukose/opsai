# Billing and gamification

Occupied-room billing stores one idempotent counter per hotel/business date. Source event content is hashed. Historical rows are immutable; correction columns exist for explicit audited admin correction and there is no payment processing.

Gamification uses an immutable XP ledger. Completion, SLA, first-time success, and quality contribute; speed alone does not. Deterministic badges are:

- Lightning Cleaner: completed, SLA-successful, quality at least 90.
- VIP Master: VIP work with quality at least 90.
- Maintenance Hero: maintenance first-time success.
- Guest Happiness: confirmed guest-happiness outcome.
- Team Player: confirmed team contribution.

Leaderboards expose display name, rank, XP, completion count, quality indicator, and badges only.
