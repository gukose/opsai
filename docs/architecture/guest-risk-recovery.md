# Guest messaging, risk, and service recovery

Guest QR sessions use 256-bit opaque random tokens. Only SHA-256 hashes are persisted; tokens contain no reservation id or guest PII. Sessions expire, can be revoked, and enforce a bounded request rate.

InternalDemo messaging deduplicates hashed provider message keys, classifies a limited safe request vocabulary, creates hotel-scoped tasks, and returns acknowledgements without employee/task details. Real Meta integration is not enabled.

Risk rule `mvp-v1` scores complaints, delay, repeated issues, poor surveys, and SLA breaches. HIGH creates human-owned service recovery. Compensation remains a separate approval decision and is never promised by AI.
