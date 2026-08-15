# Inventory, minibar, and damage

Inventory uses an immutable transaction ledger plus locked current balances. Supported movements are RECEIVE, CONSUME, ADJUST, TRANSFER, MINIBAR_CONSUMPTION, and DAMAGE_USAGE. Negative balances are rejected unless the item explicitly allows them. Operational references make repeated submissions idempotent.

Minibar completion records item quantities, decrements stock, calculates configured item prices, and creates `REVIEW_REQUIRED` charge proposals. Damage captures safe attachment provenance and optional Vision metadata. Approval records the human reviewer and may create a PMS proposal. Only a separate authorized approval posts through a capability-checked PMS adapter; AI cannot approve.
