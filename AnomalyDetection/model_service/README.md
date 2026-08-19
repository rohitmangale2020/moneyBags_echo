# Transaction Risk Scoring Service

This service loads the models created by `paysim_eda.ipynb` and exposes a scoring endpoint for the Spring Boot transaction service.

## Run locally

```powershell
cd C:\Project\AnomalyDetection\model_service
python -m pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8001
```

By default, artifacts are read from `C:\Project\AnomalyDetection\model_artifacts`. Set `MODEL_ARTIFACT_DIR` if needed.

The service uses the active version-4 balance-aware model artifact
`paysim_lightgbm_calibrated_v4.joblib`. Version 4 applies the current balance-depletion
policy and does not require an isolation-forest artifact.

## Endpoints

- `GET /health` confirms the saved models exist.
- `POST /score` returns a review-prioritization risk level; it never posts or blocks a transaction.

Example request:

```json
{
  "transaction_ref": "TRX-1001",
  "transaction_type": "TRANSFER",
  "amount": 60000,
  "old_balance_org": 90000,
  "old_balance_dest": 12000,
  "recipient_prior_tx_count": 0,
  "recipient_prior_amount": 0,
  "occurred_at": "2026-08-16T03:30:00Z"
}
```

`WITHDRAWAL` maps to PaySim `CASH_OUT`. `DEPOSIT` currently returns `NOT_SCORED` because it is outside the trained model scope.

## Spring Boot placement

Call `POST /score` after validating `TransactionRequest` and before `accountsClient.transfer()` or `accountsClient.adjust()`.

- `LOW`: normal posting.
- `MEDIUM`: extra verification or review.
- `HIGH`: hold for review before account posting.

Only approved normal transactions should update the user-behaviour profile.
