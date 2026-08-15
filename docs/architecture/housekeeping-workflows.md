# Housekeeping workflows

Housekeeping uses Task for assignment/notifications and `housekeeping_workflow` for its explicit lifecycle:

`CREATED → ASSIGNED → ACCEPTED → STARTED → PAUSED|WAITING → STARTED → INSPECTION → COMPLETED → CLOSED`.

Rejected inspection transitions to `REWORK`, then assignment/execution/inspection repeats. Inspection requires `HOUSEKEEPING_INSPECTION`; ordinary housekeeping roles receive only `HOUSEKEEPING_OPERATIONS`. Working duration is accumulated from active segments. Pause duration is accumulated separately, so wall-clock interruption time never inflates cleaning duration.

Room-ready output is disabled by default. When enabled, only a passed inspection invokes the capability-checked, idempotent PMS outbound boundary. InternalDemo updates clean status deterministically; Apaleo is unsupported.
