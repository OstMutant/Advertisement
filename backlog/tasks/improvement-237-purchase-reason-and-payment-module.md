# improvement-237: A purchase as a reason for featuring — selling, the payment module and the law around it

**Type:** feature design — what must exist for an advertiser to pay for featured placement; waits for the featured placement mechanism, which it plugs into as one more reason
**Module:** a new payment starter with its port in `platform-commons`, `marketplace-orchestrator` (composition), `marketplace-app` (purchase and "my payments" screens), `marketplace-rest-api` (the provider webhook endpoint and its security rule), the featured placement mechanism from [improvement-235](improvement-235-featured-placement-mechanism.md)
**Priority:** 🔵 later — nothing to do until the mechanism of improvement-235 exists and there is traffic; the user will come back to it then
**When:** blocked on [improvement-235](improvement-235-featured-placement-mechanism.md) (the mechanism) and, in practice, on a legal entity and a payment agreement; it comes after [improvement-236](../completed/tasks/improvement-236-list-order-and-edit-bump-rest-mcp-ui-parity.md) in the queue only because the user asked for that order

## Current state

Verified against the code on 2026-11-05: there is no payment, billing or promotion code anywhere. The platform has an audit starter that records every state change, a Testcontainers integration test layer (the owner's precondition for any money-moving code), Liquibase for schema changes, and a rule that every non-Vaadin URL has an explicit security rule.

What an earlier research (on selling placement, 2026-11-04, from official provider and law pages and press) found, kept here so it is not lost. Confidence is high where an official page was read, and each point should be re-checked before any money is taken:

- **Sell by hand first.** Before any checkout, sell a few time-limited placements by direct offer and bank-transfer invoice to five to ten shops, set the placement by an administrator, and see whether anyone pays. The owner's own notes say the same.
- **Payment providers (Ukraine):** LiqPay, WayForPay and monobank acquiring all offer a hosted payment page, so no card data touches the platform, plus a signed server callback and a status-check call. LiqPay sends base64 JSON and a signature to a `server_url` with final statuses such as success, failure, error and reversed; WayForPay posts a form to its payment page, signs with HMAC_MD5, calls back to a `serviceUrl` and expects an answer, and offers `CHECK_STATUS` and `REFUND` by order reference; monobank creates an invoice, reports status and signs its webhook with ECDSA (`X-Sign`, public key). The callback is not the only truth: the status call reconciles a lost callback.
- **A payment becomes a placement** through a status machine (new, pending, paid, failed, refunded), a webhook that verifies the signature, stores the raw callback, and changes state idempotently by the provider's reference, never trusting the browser's success redirect; every transition is audited.
- **Advertising law:** the Law of Ukraine "On Advertising", art. 9, requires advertising to be clearly separated and marked with the word "реклама"; a consumer law, 3153-IX, art. 27(1)(24), treats ranking a product higher in search without saying it is advertising or paid as unfair, but it is not in force yet. Featuring for a non-paid reason (a rating) must say so truthfully ("top rated"), not "sponsored"; only a paid reason needs "реклама". A lawyer should confirm the wording.
- **Taxes and cash registers:** whether a sole proprietor taking card payments needs a software cash register (PRRO) and a fiscal receipt per payment is disputed between sources; it needs an accountant before launch. A sole-proprietor registration and an acquiring agreement are prerequisites.
- **Search engines:** promoting one's own advertisements inside one's own pages is neither cloaking nor link spam.

## Why change

The long-term plan is to earn from visibility. The mechanism of improvement-235 gives the places to show featured entries; this task adds the one reason that brings money.

## Expected benefit

A first source of income that does not touch the reader's experience beyond what the mechanism already shows, and a payment module later paid features can reuse.

## Approach

Not started; to be planned once the mechanism exists. Questions to settle then: manual selling first and what it must prove; one placement product or tiers, duration, price and what a purchase buys (a rank, a slot, a label); a cap per advertiser and per category and city; what happens to a purchased placement when the advertisement is edited, hidden, flagged or deleted, and refunds; the provider (LiqPay, WayForPay or monobank) decided by the legal entity and the fees; the module placement under the project's rules (its own payment starter, a payment port in `platform-commons`, composition in the orchestrator); the audit trail; how the REST and MCP doors show a paid item and its label.

## Related

- [improvement-235](improvement-235-featured-placement-mechanism.md) — the mechanism this plugs into; its first reason is a rating rule.
- [improvement-236](../completed/tasks/improvement-236-list-order-and-edit-bump-rest-mcp-ui-parity.md) — the default list order and the free bump on edit, which undermine any paid raise until settled.
- The private product notes (outside version control): the roadmap and the payments and subscriptions feature specs; read them as input when planning, quote nothing from them into a checked-in file.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
