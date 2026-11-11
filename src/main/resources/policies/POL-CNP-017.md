---
documentId: POL-CNP-017
title: Card-Not-Present Dispute Handling Procedure
version: 3.2
effectiveDate: 2026-10-01
owner: Payment Fraud Operations
status: ACTIVE
classification: SYNTHETIC_TRAINING
---

# Card-Not-Present Dispute Handling Procedure (Synthetic Training Document)

## 3.1 Scope

This procedure applies to card-not-present disputes where the customer reports a merchant they do not recognise or a transaction they did not authorise. It is synthetic training content and must not be treated as a real bank policy.

## 3.2 Initial fraud routing

When a customer denies recognising the merchant and independent signals show a newly observed device, a location anomaly inconsistent with trusted activity, or an unavailable authentication outcome, the case must be routed to the FRAUD_INVESTIGATION queue for human review. The assistant may only recommend the route; a reviewer confirms it. High-risk combinations of device and location anomalies indicate a priority of P1 with a four-hour review target.

## 3.3 Evidence requirements

Before a recommendation is finalised, the investigation must record the transaction status, the device trust level, the location comparison outcome, the merchant risk band and any prior dispute history. If required evidence is unavailable or contradictory, the recommendation must mark the missing items and route the case to MANUAL_REVIEW rather than inventing facts.

## 3.4 Prohibited automated actions

Under no circumstances may the investigation assistant block a card, issue a refund or credit, move funds, file a regulatory report or contact the customer. Those actions require an authorised human actor outside this application.
