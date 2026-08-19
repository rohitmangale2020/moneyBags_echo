# Risk Service Setup

Use this only after `moneyBags_echo` is already running.

## Model files

The `AnomalyDetection` folder is included inside the `moneyBags_echo` repository:

```text
C:\Project\moneyBags_echo\
├── AnomalyDetection\
│   ├── model_service\
│   │   ├── app.py
│   │   ├── requirements.txt
│   │   └── run_model_service.ps1
│   └── model_artifacts\
│       └── paysim_lightgbm_calibrated_v4.joblib
└── risk-service\
```

Do not commit `.venv`, notebooks, old model files, or training data.

## Install once

Install Python 3.14+.

## Start the Python model

Open a PowerShell window:

```powershell
cd C:\Project\moneyBags_echo\AnomalyDetection\model_service
.\run_model_service.ps1
```

Leave this window running. The model starts at `http://localhost:8001`.

## Start the Java risk service

Open a second PowerShell window:

```powershell
cd C:\Project\moneyBags_echo
mvn -pl risk-service spring-boot:run
```

Leave this window running. The risk service starts at `http://localhost:8089`.

## Verify

```powershell
cd C:\Project\moneyBags_echo\AnomalyDetection\model_service
.\run_model_service.ps1 -Action Health
```

The response must contain:

```json
{ "status": "ok" }
```

Restart the MoneyBags `transactions` service after starting the risk service.
