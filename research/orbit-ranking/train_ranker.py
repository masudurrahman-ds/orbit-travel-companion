import argparse, json
from pathlib import Path
import matplotlib.pyplot as plt
import pandas as pd
import torch
from torch import nn
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler

FEATURES = ["budget_fit", "interest_match", "season_fit", "travel_time_fit", "data_confidence"]

class Ranker(nn.Module):
    def __init__(self):
        super().__init__()
        self.net = nn.Sequential(nn.Linear(len(FEATURES), 12), nn.ReLU(), nn.Linear(12, 1))
    def forward(self, x):
        return self.net(x).squeeze(-1)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--data", required=True)
    p.add_argument("--epochs", type=int, default=80)
    p.add_argument("--out", required=True)
    a = p.parse_args()
    df = pd.read_csv(a.data, comment="#").dropna(subset=FEATURES + ["label"])
    if len(df) < 4 or df.label.nunique() < 2:
        raise SystemExit("Need at least four rows and two label classes.")
    x = df[FEATURES].astype("float32").to_numpy()
    y = df.label.astype("float32").to_numpy()
    xt, xv, yt, yv = train_test_split(x, y, test_size=.25, random_state=42, stratify=y)
    scaler = StandardScaler().fit(xt)
    xt, xv = scaler.transform(xt).astype("float32"), scaler.transform(xv).astype("float32")
    torch.manual_seed(42)
    model = Ranker()
    opt = torch.optim.AdamW(model.parameters(), lr=.01, weight_decay=1e-4)
    loss_fn = nn.BCEWithLogitsLoss()
    tx, ty, vx, vy = map(torch.tensor, (xt, yt, xv, yv))
    train_losses, val_losses = [], []
    for _ in range(a.epochs):
        model.train()
        opt.zero_grad()
        loss = loss_fn(model(tx), ty)
        loss.backward()
        opt.step()
        model.eval()
        with torch.no_grad():
            vl = loss_fn(model(vx), vy).item()
        train_losses.append(float(loss.item()))
        val_losses.append(float(vl))
    layers = []
    for layer in model.net:
        if isinstance(layer, nn.Linear):
            layers.append({"type":"linear", "weights":layer.weight.detach().tolist(), "bias":layer.bias.detach().tolist()})
        elif isinstance(layer, nn.ReLU):
            layers.append({"type":"relu"})
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"model_name":"ORBIT destination ranker", "version":"0.1.0",
        "feature_order":FEATURES, "mean":scaler.mean_.tolist(), "scale":scaler.scale_.tolist(),
        "layers":layers, "warning":"Starter labels are illustrative, not validated research evidence."}, indent=2))
    plt.figure()
    plt.plot(train_losses, label="train")
    plt.plot(val_losses, label="validation")
    plt.xlabel("Epoch")
    plt.ylabel("Binary cross-entropy")
    plt.title("ORBIT ranker training loss")
    plt.legend()
    plt.tight_layout()
    plt.savefig(out.with_suffix(".loss.png"), dpi=160)
    print("Exported", out, "and loss plot. Starter labels are not research evidence.")

if __name__ == "__main__":
    main()
