# MVP DEMO walkthrough

All accounts are hotel-scoped. Passwords come from their matching environment variables.

| Email | Role | Reviewer focus |
|---|---|---|
| `gm@demo.hotelopai.app` | General Manager | dashboard, KPI, approvals, risk, billing, leaderboard |
| `housekeeping.supervisor@demo.hotelopai.app` | Housekeeping Supervisor | inspection, reassignment, rework, handover |
| `housekeeper@demo.hotelopai.app` | Housekeeper | cleaning, pause/resume, minibar, damage/photo, voice |
| `technician@demo.hotelopai.app` | Technician | HVAC/electrical/plumbing, Voice and Vision proposals |
| `reception@demo.hotelopai.app` | Front Office | guest requests, approvals, service recovery |
| `guest.relations@demo.hotelopai.app` | Guest Relations | messaging, satisfaction, risk and follow-up |
| `reviewer.admin@demo.hotelopai.app` | Admin Reviewer | all DEMO operations |

## Hotel-day scenario

1. Process the room 302 checkout fixture.
2. Verify Departure Cleaning is automatically assigned to the active housekeeper.
3. Trigger Flash Minibar during cleaning; verify cleaning pauses, inventory changes, and charge awaits human approval.
4. Review the charge as Front Office, complete flash work, and verify cleaning resumes.
5. Complete cleaning, approve inspection as Supervisor, and verify READY plus InternalDemo room-ready.

## Additional scenarios

- Say “Room 302 air conditioning is not working,” review transcript/proposal, confirm, and verify HVAC technician assignment.
- Select a deterministic Vision issue fixture and confirm its advisory task proposal. Vision is visibly simulated.
- Resolve a QR guest request and verify safe acknowledgement.
- Submit poor satisfaction plus complaint/delay; verify HIGH risk and human service recovery.
- As GM, review KPI, billing correction history, gamification and leaderboard.

Minibar, damage, compensation and PMS charges always require authorized human approval.
