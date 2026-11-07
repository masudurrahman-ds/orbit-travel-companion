# ORBIT ranking research add-on

This is an additive CPU-friendly PyTorch ranking experiment for the existing Android app. It does not replace the existing planner.

## Run
```bash
cd research/orbit-ranking
python -m venv .venv
# activate the environment, then:
pip install -r requirements.txt
python train_ranker.py --data data/ranking_examples.csv --epochs 80 --out android-assets/orbit_ranker.json
```

The included rows are illustrative demonstration labels, not real tourist preference data and not evidence of model quality. Collect a larger, consented and carefully labelled dataset before research claims.

The trainer exports numeric weights and normalisation values. Kotlin can execute this small MLP with ordinary math, avoiding a PyTorch runtime dependency in the APK. See OrbitRanker.kt.

## Data integrity
The repo's 113 destination catalogue remains the source of destination identity. Enrich each place with verified coordinates, image metadata, hotels, restaurants and dated price observations. Do not invent prices or imply live availability. Image search results are candidates only: verify the exact licence and attribution before app use.
