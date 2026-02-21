# 📊 Alpha Capital — Hedge Fund Portfolio Risk Analytics

<div align="center">

![Python](https://img.shields.io/badge/Python-3.11-3776AB?style=flat-square&logo=python&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-ES6-F7DF1E?style=flat-square&logo=javascript&logoColor=black)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Excel](https://img.shields.io/badge/Excel-Financial%20Model-217346?style=flat-square&logo=microsoftexcel&logoColor=white)
![Status](https://img.shields.io/badge/Status-Portfolio%20Project-C8920A?style=flat-square)

**Full-stack quantitative finance system** — Portfolio optimization, real-time risk analytics, and interactive dashboard for a $500M AUM Long/Short Equity Hedge Fund.

[Live Demo](#) · [Excel Model](#) · [Documentation](#methodology)

</div>

---

## 🧩 The Problem

**Alpha Capital Management** ($500M AUM) detected critical weaknesses in its equity portfolio:

| Issue | Detail |
|-------|--------|
| Underperformance | Sharpe Ratio at **0.948** — below the 1.0 minimum acceptable threshold |
| Overconcentration | Technology sector at **53%** — exceeds the 45% sector limit |
| Tail Risk | CVaR 99% = **$16.1M/day** in potential losses |
| Systematic Risk | Portfolio Beta = **1.111** — excessive market sensitivity |

The Risk Committee required a full quantitative analysis, rebalancing recommendation, and regulatory risk reporting — implemented across three technology layers.

---

## ✅ Results

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Sharpe Ratio** | 0.948 | 1.296 | **+36.7% ↑** |
| **Ann. Return** | 23.2% | 29.9% | +670 bps |
| **CVaR 99% (1d)** | $16.1M | $15.7M | Reduced |
| **Portfolio Beta** | 1.111 | 1.073 | Less systematic risk |

**Monte Carlo 12-Month Projection (Optimal Portfolio, 10,000 scenarios):**

```
P5  (Pessimistic) →  ~$488M   (-2.4%)
P50 (Base Case)   →  ~$664M  (+32.8%)
P95 (Optimistic)  →  ~$907M  (+81.5%)
```

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  ALPHA CAPITAL SYSTEM                    │
├───────────────┬──────────────────┬──────────────────────┤
│   Python      │   JavaScript     │   Java               │
│   Engine      │   Dashboard      │   Risk Engine        │
├───────────────┼──────────────────┼──────────────────────┤
│ Optimization  │ Interactive UI   │ OOP Risk Calculator  │
│ VaR / CVaR    │ Live Ticker      │ VaR / CVaR / MRC     │
│ Monte Carlo   │ Monte Carlo Viz  │ Constraint Validator │
│ Data Export   │ Sector Donut     │ Executive Report     │
└───────────────┴──────────────────┴──────────────────────┘
         │               │                  │
         └───────────────┴──────────────────┘
                         │
              ┌──────────────────┐
              │   Excel Model    │
              │ KPI Dashboard    │
              │ Risk Metrics     │
              │ Methodology      │
              └──────────────────┘
```

---

## 🛠️ Tech Stack

### Python (`portfolio_analysis.py`)
- **SciPy** `optimize.minimize` — Markowitz mean-variance optimization
- **NumPy** — Cholesky decomposition, matrix operations, Monte Carlo simulation
- **Pandas** — Time series management, 3-year historical returns (756 trading days)
- **openpyxl** — Programmatic Excel dashboard generation

### JavaScript (`AlphaCapital_Dashboard.html`)
- Vanilla ES6 — zero dependencies, runs in any browser
- Canvas API — real-time Monte Carlo path simulation
- SVG — animated gauges and sector donut chart
- CSS animations — staggered reveals, ticker tape, micro-interactions

### Java (`HedgeFundRiskEngine.java`)
- OOP design with `Position` and `Portfolio` classes
- Matrix operations for covariance and marginal risk contribution
- Constraint validation engine with automated breach alerts
- Designed as standalone microservice (REST-API ready)

### Excel (`AlphaCapital_HedgeFund_Analysis.xlsx`)
- 3 worksheets: Executive Dashboard · Risk Metrics · Methodology
- Industry-standard color coding (blue inputs / black formulas / green cross-links)
- Dynamic formulas — all values recalculate when assumptions change

---

## 📐 Methodology

### Markowitz Mean-Variance Optimization
```
maximize:   (w'μ - Rᶠ) / √(w'Σw)       ← Sharpe Ratio

subject to: Σwᵢ = 1                      ← Full investment
            0.02 ≤ wᵢ ≤ 0.25             ← Position limits
```

### Value at Risk (Parametric)
```
VaR₉₉% = -(μ_daily - z₀.₀₁ · σ_daily) · AUM
```

### Conditional VaR / Expected Shortfall
```
CVaR₉₉% = -(μ_daily - σ_daily · φ(z) / α) · AUM
```
where `φ(z)` is the standard normal PDF evaluated at z = 2.3263

### Marginal Risk Contribution
```
MRCᵢ = (wᵢ · Σⱼ wⱼ σᵢ σⱼ ρᵢⱼ) / σₚ
```

### Portfolio Beta
```
β = Cov(Rₚ, R_benchmark) / Var(R_benchmark)
```

---

## 🚀 Getting Started

### Prerequisites
```bash
Python 3.9+    pip install numpy pandas scipy openpyxl
Java 17+       javac / java
Browser        Any modern browser (Chrome, Firefox, Safari)
```

### Run Python Engine
```bash
git clone https://github.com/yourusername/alpha-capital-risk-analytics
cd alpha-capital-risk-analytics

pip install -r requirements.txt
python portfolio_analysis.py
```

### Run Java Risk Engine
```bash
javac HedgeFundRiskEngine.java
java HedgeFundRiskEngine
```

### Launch Interactive Dashboard
```bash
# Simply open in browser — no server required
open AlphaCapital_Dashboard.html
```

### Open Excel Model
```
Open AlphaCapital_HedgeFund_Analysis.xlsx
Blue cells = editable inputs (weights, rates, AUM)
All formulas recalculate automatically
```

---

## 📁 Project Structure

```
alpha-capital-risk-analytics/
│
├── portfolio_analysis.py               # Python optimization & risk engine
├── HedgeFundRiskEngine.java            # Java OOP real-time risk calculator
├── AlphaCapital_Dashboard.html         # Interactive JavaScript dashboard
├── AlphaCapital_HedgeFund_Analysis.xlsx  # Excel financial model
│
├── data/
│   ├── returns_data.csv                # Simulated 3-year daily returns
│   └── prices_data.csv                 # Simulated historical prices
│
├── requirements.txt                    # Python dependencies
└── README.md
```

---

## 📊 Portfolio Universe

| Ticker | Sector | Current | Optimal | Change |
|--------|--------|---------|---------|--------|
| AAPL | Technology | 18.0% | 25.0% | ▲ +7.0% |
| MSFT | Technology | 15.0% | 25.0% | ▲ +10.0% |
| GOOGL | Technology | 12.0% | 4.7% | ▼ -7.3% |
| AMZN | Consumer Disc. | 10.0% | 2.0% | ▼ -8.0% |
| NVDA | Technology | 8.0% | 10.3% | ▲ +2.3% |
| JPM | Financials | 12.0% | 2.0% | ▼ -10.0% |
| GS | Financials | 8.0% | 2.0% | ▼ -6.0% |
| BAC | Financials | 7.0% | 2.0% | ▼ -5.0% |
| XOM | Energy | 5.0% | 2.0% | ▼ -3.0% |
| JNJ | Healthcare | 5.0% | 25.0% | ▲ +20.0% |

---

## ⚠️ Disclaimer

This project uses **simulated data** for educational and portfolio demonstration purposes only. It does not constitute financial advice. Past simulated performance does not predict future results. All risk metrics assume normally distributed returns.

---

## 👤 Author Juan Parra

Built as a quantitative finance portfolio project demonstrating applied Modern Portfolio Theory, regulatory risk analytics, and full-stack financial systems engineering.

*Python · JavaScript · Java · Excel · February 2026*
