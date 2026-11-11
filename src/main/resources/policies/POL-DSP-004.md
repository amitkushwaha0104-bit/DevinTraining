---
documentId: POL-DSP-004
title: Merchant Dispute and Fulfilment Procedure
version: 1.4
effectiveDate: 2026-09-15
owner: Dispute Operations
status: ACTIVE
classification: SYNTHETIC_TRAINING
---

# Merchant Dispute and Fulfilment Procedure (Synthetic Training Document)

## 1.1 Scope

This procedure applies when the customer recognises the merchant but disputes the fulfilment of goods or services, a cancellation that was not honoured, or a charged amount that differs from the agreed price.

## 1.2 Routing to dispute operations

Cases where the merchant is recognised and the complaint concerns fulfilment, cancellation or amount must be recommended for the DISPUTE_OPERATIONS queue. The standard resolution target is seventy-two hours at P3 priority unless the amount involved is exceptional.

## 1.3 Evidence requirements

The investigation should confirm the transaction status is settled or posted, capture the customer statement verbatim, and check prior disputes for the same merchant. Where the customer cannot supply order references or cancellation confirmations, the missing information must be listed on the recommendation.
