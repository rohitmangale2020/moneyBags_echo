"""HTTP scoring service for the PaySim-trained transaction-risk models.

It returns a review-prioritization risk level. Spring Boot owns all transaction
posting, verification, hold, and audit decisions.
"""

from __future__ import annotations

import os
from datetime import datetime
from functools import lru_cache
from pathlib import Path
from typing import Literal

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent.parent
ARTIFACT_DIR = Path(os.getenv("MODEL_ARTIFACT_DIR", BASE_DIR / "model_artifacts"))
# Version 4 is the active balance-aware policy.  An explicit MODEL_VERSION
# environment variable may still be used for offline model comparison.
MODEL_VERSION = os.getenv("MODEL_VERSION", "v4")
LGBM_ARTIFACT = ARTIFACT_DIR / f"paysim_lightgbm_calibrated_{MODEL_VERSION}.joblib"
IF_ARTIFACT = ARTIFACT_DIR / f"paysim_isolation_forest_{MODEL_VERSION}.joblib"
USES_ISOLATION_FOREST = MODEL_VERSION != "v4"

app = FastAPI(title="MoneyBags Transaction Risk Scorer", version="1.0.0")


class ScoreRequest(BaseModel):
    transaction_ref: str = Field(min_length=1, max_length=40)
    transaction_type: Literal["TRANSFER", "WITHDRAWAL", "CASH_OUT", "DEPOSIT"]
    amount: float = Field(gt=0)
    old_balance_org: float = Field(ge=0)
    old_balance_dest: float = Field(ge=0)
    recipient_prior_tx_count: int = Field(default=0, ge=0)
    recipient_prior_amount: float = Field(default=0, ge=0)
    occurred_at: datetime | None = None


class ScoreResponse(BaseModel):
    transaction_ref: str
    model_coverage: bool
    model_transaction_type: str | None
    calibrated_lightgbm_score: float | None
    isolation_forest_score: float | None
    final_risk_score: float | None
    risk_level: Literal["LOW", "MEDIUM", "HIGH", "NOT_SCORED"]
    review_recommended: bool
    reasons: list[str]
    model_versions: dict[str, str]


def paysim_type(transaction_type: str) -> str | None:
    if transaction_type == "TRANSFER":
        return "TRANSFER"
    if transaction_type in {"WITHDRAWAL", "CASH_OUT"}:
        return "CASH_OUT"
    return None


def model_frame(request: ScoreRequest, model_type: str) -> pd.DataFrame:
    # Use pre-posting values only. These describe whether the transaction
    # drains the sender account, rather than relying on its time of day.
    origin_balance = max(request.old_balance_org, 1.0)
    destination_balance = max(request.old_balance_dest, 1.0)
    origin_depletion_ratio = min(request.amount / origin_balance, 1.0)
    return pd.DataFrame([{
        "type": model_type,
        "log_amount": np.log1p(request.amount),
        "log_oldbalance_org": np.log1p(request.old_balance_org),
        "log_oldbalance_dest": np.log1p(request.old_balance_dest),
        "amount_to_origin_balance_ratio": request.amount / origin_balance,
        "amount_to_destination_balance_ratio": request.amount / destination_balance,
        "estimated_origin_balance_after": max(request.old_balance_org - request.amount, 0.0),
        "origin_balance_depletion_ratio": origin_depletion_ratio,
        "nearly_empties_origin_account": int(origin_depletion_ratio >= 0.90),
        "log_recipient_prior_tx_count": np.log1p(request.recipient_prior_tx_count),
        "log_recipient_prior_amount": np.log1p(request.recipient_prior_amount),
    }])


def smoothstep(value: float, start: float, end: float) -> float:
    """Map a continuous business signal into [0, 1] without a hard jump."""
    ratio = min(1.0, max(0.0, (value - start) / (end - start)))
    return ratio * ratio * (3.0 - 2.0 * ratio)


def balance_aware_policy(request: ScoreRequest, lgb_score: float,
                         medium_threshold: float, high_threshold: float) -> tuple[float, list[str]]:
    """Blend learned fraud probability with live pre-posting balance context.

    This deliberately has no OR/veto path: all signals contribute continuously
    to one final review score.  It protects the business case PaySim labels do
    not represent well—large depletion of a customer's current account.
    """
    origin_balance = max(request.old_balance_org, 1.0)
    destination_balance = max(request.old_balance_dest, 1.0)
    depletion_ratio = min(request.amount / origin_balance, 1.0)
    destination_ratio = request.amount / destination_balance
    lgb_signal = smoothstep(lgb_score, medium_threshold, high_threshold)
    # Smaller retail balances are more vulnerable to a large relative debit.  The
    # policy remains continuous—this is a weighted risk signal, not a hard veto.
    # At <= 50,000, 75% depletion reaches the strongest balance signal (for
    # example, 15,000 from a 20,000 balance).  Larger balances retain V4's
    # original 50%-95% curve.
    small_balance_signal = smoothstep(np.log1p(100_000.0) - np.log1p(origin_balance),
                                      0.0, np.log1p(100_000.0) - np.log1p(50_000.0))
    depletion_start = 0.50
    depletion_end = 0.95 - (0.20 * small_balance_signal)
    depletion_signal = smoothstep(depletion_ratio, depletion_start, depletion_end)
    # A nearly empty destination alone is not sufficient; it matters only when
    # a material share of the source account is also being moved.
    concentration_signal = depletion_signal * smoothstep(np.log1p(destination_ratio), np.log1p(0.50), np.log1p(10.0))
    depletion_weight = 0.40 + (0.20 * small_balance_signal)
    lgb_weight = 0.45 - (0.20 * small_balance_signal)
    final_score = lgb_weight * lgb_signal + depletion_weight * depletion_signal + 0.15 * concentration_signal
    reasons: list[str] = []
    if lgb_signal > 0:
        reasons.append("Calibrated LightGBM contributes to the review score.")
    if depletion_signal >= 0.10:
        reasons.append(f"Transaction uses {depletion_ratio:.0%} of the source account balance.")
    if small_balance_signal >= 0.10:
        reasons.append("Small-balance depletion policy increases the review score.")
    if concentration_signal >= 0.10:
        reasons.append("Destination-balance concentration increases the review score.")
    return float(final_score), reasons


@lru_cache(maxsize=1)
def load_artifacts() -> tuple[dict, dict | None]:
    if not LGBM_ARTIFACT.exists() or (USES_ISOLATION_FOREST and not IF_ARTIFACT.exists()):
        raise FileNotFoundError(
            f"Model artifact missing for {MODEL_VERSION}. Run the matching notebook model sections first, then set "
            "MODEL_ARTIFACT_DIR if needed."
        )
    return joblib.load(LGBM_ARTIFACT), joblib.load(IF_ARTIFACT) if USES_ISOLATION_FOREST else None


@app.get("/health")
def health() -> dict:
    return {
        "status": "ok" if LGBM_ARTIFACT.exists() and (not USES_ISOLATION_FOREST or IF_ARTIFACT.exists()) else "model_artifact_missing",
        "artifact_directory": str(ARTIFACT_DIR),
    }


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    model_type = paysim_type(request.transaction_type)
    if model_type is None:
        return ScoreResponse(
            transaction_ref=request.transaction_ref,
            model_coverage=False,
            model_transaction_type=None,
            calibrated_lightgbm_score=None,
            isolation_forest_score=None,
            final_risk_score=None,
            risk_level="NOT_SCORED",
            review_recommended=False,
            reasons=["Transaction type is outside the current PaySim-trained model scope."],
            model_versions={},
        )

    try:
        lightgbm_bundle, isolation_bundle = load_artifacts()
    except FileNotFoundError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    frame = model_frame(request, model_type)

    lgb_matrix = lightgbm_bundle["preprocessor"].transform(
        frame[lightgbm_bundle["feature_columns"]]
    )
    raw_lgb_score = lightgbm_bundle["model"].predict_proba(lgb_matrix)[:, 1]
    lgb_score = float(
        lightgbm_bundle["score_calibrator"].predict_proba(raw_lgb_score.reshape(-1, 1))[:, 1][0]
    )

    if MODEL_VERSION == "v4":
        medium_threshold = float(lightgbm_bundle["medium_thresholds"][model_type])
        high_threshold = float(lightgbm_bundle["high_thresholds"][model_type])
        final_score, reasons = balance_aware_policy(request, lgb_score, medium_threshold, high_threshold)
        risk_level = "HIGH" if final_score >= 0.55 else "MEDIUM" if final_score >= 0.35 else "LOW"
        if not reasons:
            reasons.append("No model review threshold was exceeded.")
        return ScoreResponse(
            transaction_ref=request.transaction_ref, model_coverage=True, model_transaction_type=model_type,
            calibrated_lightgbm_score=lgb_score, isolation_forest_score=None, final_risk_score=final_score,
            risk_level=risk_level, review_recommended=risk_level in {"MEDIUM", "HIGH"}, reasons=reasons,
            model_versions={"lightgbm": lightgbm_bundle["model_version"], "policy": "balance-aware-blend-v1"},
        )

    assert isolation_bundle is not None
    if_matrix = isolation_bundle["preprocessor"].transform(frame[isolation_bundle["feature_columns"]])
    isolation_score = float(-isolation_bundle["model"].score_samples(if_matrix)[0])
    lgb_alert = lgb_score >= float(lightgbm_bundle["type_thresholds"][model_type])
    if_alert = isolation_score >= float(isolation_bundle["high_threshold"])
    reasons: list[str] = []
    if lgb_alert:
        reasons.append(f"Calibrated LightGBM score exceeds the {model_type} review threshold.")
    if if_alert:
        reasons.append("Isolation Forest identifies an unusual transaction pattern.")

    # Model signals prioritize review. They are never an automatic fraud verdict.
    risk_level = "HIGH" if lgb_alert and if_alert else "MEDIUM" if lgb_alert or if_alert else "LOW"
    if not reasons:
        reasons.append("No model review threshold was exceeded.")

    return ScoreResponse(
        transaction_ref=request.transaction_ref,
        model_coverage=True,
        model_transaction_type=model_type,
        calibrated_lightgbm_score=lgb_score,
        isolation_forest_score=isolation_score,
        final_risk_score=None,
        risk_level=risk_level,
        review_recommended=risk_level in {"MEDIUM", "HIGH"},
        reasons=reasons,
        model_versions={
            "lightgbm": lightgbm_bundle["model_version"],
            "isolation_forest": isolation_bundle["model_version"],
        },
    )
