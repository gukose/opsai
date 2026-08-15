# Workforce assignment

Candidate eligibility is deterministic: hotel, active employment, operational availability, active shift, required role, skill/level, and language are hard filters. Department, availability, no active task, home area, and workload determine stable ranking. Employee number and display name are tie-breakers.

Unavailable staff are never selected for normal work. An emergency with no candidate returns an escalation explanation; it does not silently violate availability. Supervisors may use the existing explicit assignment endpoint to override.

Operational states are `AVAILABLE`, `WORKING`, `BREAK`, `LUNCH`, `MEETING`, `TRAINING`, `OFFLINE`, and `ON_LEAVE`.
