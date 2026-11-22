#!/usr/bin/env python3
"""Train a compact three-class SMS text classifier for ShieldXMobile.

The raw dataset is downloaded into a temporary file and is never committed.
The generated model is a TF-IDF + multinomial logistic-regression classifier
serialized as JSON for lightweight on-device inference in Kotlin.
"""
from __future__ import annotations

import json
import re
import tempfile
import urllib.request
from collections import Counter
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, f1_score
from sklearn.model_selection import train_test_split

ROOT = Path(__file__).resolve().parents[1]
MODEL_PATH = ROOT / "app/src/main/assets/sms_model.json"
REPORT_PATH = ROOT / "ml/generated/evaluation.json"

# Pin the public mirror to a fixed commit so later upstream changes cannot
# silently alter a training run. The source is the Mishra & Soni Mendeley V1
# dataset (CC BY 4.0); details and attribution are in ml/README.md.
DATASET_URL = (
    "https://raw.githubusercontent.com/MarazMia/SMISH_DT/"
    "c63f6d58e6deaddd11d0dae7fe9d52c383fd2c7d/"
    "Dataset/Mendeley_Dataset_5971.csv"
)
RANDOM_STATE = 42
MAX_FEATURES = 6000
LABEL_MAP = {
    "ham": "ham",
    "legitimate": "ham",
    "spam": "spam",
    "smishing": "smishing",
}


def download_dataset() -> pd.DataFrame:
    request = urllib.request.Request(
        DATASET_URL,
        headers={"User-Agent": "ShieldXMobile-SMS-Model-Training/1.0"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        payload = response.read()
    if len(payload) < 20_000:
        raise RuntimeError(f"Dataset download unexpectedly small: {len(payload)} bytes")
    with tempfile.NamedTemporaryFile(suffix=".csv") as temporary:
        temporary.write(payload)
        temporary.flush()
        frame = pd.read_csv(temporary.name, encoding="utf-8", encoding_errors="replace")
    required = {"LABEL", "TEXT"}
    if not required.issubset(frame.columns):
        raise ValueError(f"Dataset is missing required columns {sorted(required)}")
    return frame


def clean_dataset(frame: pd.DataFrame) -> tuple[pd.DataFrame, dict]:
    data = frame[["LABEL", "TEXT"]].copy()
    data["label"] = data["LABEL"].astype(str).str.strip().str.lower().map(LABEL_MAP)
    data["text"] = data["TEXT"].fillna("").astype(str).str.strip()
    data = data[data["label"].notna() & data["text"].ne("")].copy()

    # Avoid duplicate-message leakage and exclude duplicate texts with
    # contradictory labels rather than choosing one arbitrarily.
    data["normalized"] = data["text"].str.lower().str.replace(r"\s+", " ", regex=True).str.strip()
    conflicting = data.groupby("normalized")["label"].nunique()
    conflict_keys = set(conflicting[conflicting > 1].index)
    conflict_rows = int(data["normalized"].isin(conflict_keys).sum())
    data = data[~data["normalized"].isin(conflict_keys)].copy()
    before_dedupe = len(data)
    data = data.drop_duplicates(subset=["normalized"], keep="first").reset_index(drop=True)

    counts = Counter(data["label"])
    expected = {"ham", "spam", "smishing"}
    if set(counts) != expected or min(counts.values()) < 5:
        raise ValueError(f"Need all 3 classes with at least 5 rows each; found {dict(counts)}")

    stats = {
        "source_rows": int(len(frame)),
        "usable_rows_before_deduplication": int(before_dedupe + conflict_rows),
        "conflicting_rows_removed": conflict_rows,
        "duplicate_rows_removed": int(before_dedupe - len(data)),
        "training_rows_after_cleaning": int(len(data)),
        "class_counts_after_cleaning": dict(sorted(counts.items())),
    }
    return data, stats


def make_vectorizer() -> TfidfVectorizer:
    return TfidfVectorizer(
        lowercase=True,
        strip_accents="unicode",
        token_pattern=r"(?u)\b\w\w+\b",
        ngram_range=(1, 2),
        min_df=2,
        max_features=MAX_FEATURES,
        sublinear_tf=True,
        norm="l2",
        dtype=np.float32,
    )


def main() -> None:
    raw = download_dataset()
    data, cleaning = clean_dataset(raw)
    texts = data["text"].tolist()
    labels = data["label"].tolist()

    x_train, x_test, y_train, y_test = train_test_split(
        texts,
        labels,
        test_size=0.20,
        random_state=RANDOM_STATE,
        stratify=labels,
    )

    evaluation_vectorizer = make_vectorizer()
    train_matrix = evaluation_vectorizer.fit_transform(x_train)
    test_matrix = evaluation_vectorizer.transform(x_test)
    evaluation_model = LogisticRegression(
        C=2.0,
        max_iter=2000,
        class_weight="balanced",
        random_state=RANDOM_STATE,
    )
    evaluation_model.fit(train_matrix, y_train)
    predictions = evaluation_model.predict(test_matrix)
    classes = evaluation_model.classes_.tolist()

    report = classification_report(
        y_test,
        predictions,
        labels=classes,
        output_dict=True,
        zero_division=0,
    )
    metrics = {
        "accuracy": float(accuracy_score(y_test, predictions)),
        "macro_f1": float(f1_score(y_test, predictions, average="macro")),
        "weighted_f1": float(f1_score(y_test, predictions, average="weighted")),
        "class_report": report,
        "confusion_matrix": confusion_matrix(y_test, predictions, labels=classes).tolist(),
        "class_order": classes,
        "test_rows": len(y_test),
    }

    # Refit the deployable model on all cleaned rows after the untouched
    # stratified holdout has been evaluated.
    final_vectorizer = make_vectorizer()
    full_matrix = final_vectorizer.fit_transform(texts)
    final_model = LogisticRegression(
        C=2.0,
        max_iter=2000,
        class_weight="balanced",
        random_state=RANDOM_STATE,
    )
    final_model.fit(full_matrix, labels)

    vocabulary = {
        token: int(index)
        for token, index in final_vectorizer.vocabulary_.items()
    }
    artifact = {
        "format": "shieldx-tfidf-logreg-v1",
        "classes": final_model.classes_.tolist(),
        "vocabulary": vocabulary,
        "idf": [round(float(value), 8) for value in final_vectorizer.idf_],
        "coefficients": [
            [round(float(value), 8) for value in row]
            for row in final_model.coef_
        ],
        "intercepts": [round(float(value), 8) for value in final_model.intercept_],
        "preprocessing": {
            "lowercase": True,
            "unicode_accent_stripping": True,
            "token_pattern": r"(?u)\b\w\w+\b",
            "ngram_range": [1, 2],
            "sublinear_tf": True,
            "l2_normalization": True,
        },
        "training": {
            "dataset": "Mishra & Soni SMS Phishing Dataset, Mendeley Data V1",
            "doi": "10.17632/f45bkkt8pr.1",
            "cleaned_rows": len(data),
            "feature_count": len(vocabulary),
            "random_state": RANDOM_STATE,
            "holdout_accuracy": metrics["accuracy"],
            "holdout_macro_f1": metrics["macro_f1"],
        },
    }

    MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    MODEL_PATH.write_text(json.dumps(artifact, separators=(",", ":")), encoding="utf-8")
    REPORT_PATH.write_text(
        json.dumps(
            {
                "source": {
                    "title": "SMS PHISHING DATASET FOR MACHINE LEARNING AND PATTERN RECOGNITION",
                    "authors": ["Sandhya Mishra", "Devpriya Soni"],
                    "doi": "10.17632/f45bkkt8pr.1",
                    "license": "CC BY 4.0",
                    **cleaning,
                },
                "holdout_evaluation": metrics,
                "deployed_model": {
                    "type": "TF-IDF + Logistic Regression",
                    "feature_count": len(vocabulary),
                    "classes": final_model.classes_.tolist(),
                },
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(json.dumps({
        "model_path": str(MODEL_PATH),
        "report_path": str(REPORT_PATH),
        "cleaning": cleaning,
        "accuracy": metrics["accuracy"],
        "macro_f1": metrics["macro_f1"],
        "feature_count": len(vocabulary),
    }, indent=2))


if __name__ == "__main__":
    main()
