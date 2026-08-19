# Risk Service Setup

Use this only after `moneyBags_echo` is already running.

## Model files

The `AnomalyDetection` folder is included inside the `moneyBags_echo` repository:

```text
C:\Project\moneyBags_echo\
├── AnomalyDetection\
│   ├── model_service\
│   │   ├── app.py
│   │   └── requirements.txt
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
py -3.14 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```

Leave this window running. The model starts at `http://localhost:8001`.

## Start the Java risk service

just start the spring boot risk-service app as you are used to opening everything else

## Verify

In a separate PowerShell window, run:

```powershell
Invoke-RestMethod http://127.0.0.1:8001/health
```

The response must contain:

```json
{ "status": "ok" }
```

Restart the MoneyBags `transactions` service after starting the risk service.
