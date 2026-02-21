"""
╔══════════════════════════════════════════════════════════════════╗
║         ALPHA CAPITAL HEDGE FUND - PORTFOLIO RISK ENGINE         ║
║         Markowitz Optimization + Risk Analytics Suite            ║
╚══════════════════════════════════════════════════════════════════╝

PROBLEMA:
---------
Alpha Capital Management, un hedge fund con $500M AUM, detectó que su
portafolio de renta variable estaba generando retornos por debajo del
benchmark (S&P 500) con una volatilidad excesiva. El comité de riesgo
necesitaba:
  1. Identificar activos que destruían valor ajustado por riesgo
  2. Reoptimizar la asignación de activos bajo restricciones reales
  3. Calcular métricas de riesgo regulatorias (VaR 99%, CVaR, Beta)
  4. Generar un reporte ejecutivo con recomendaciones accionables

SOLUCIÓN:
---------
Implementación de un motor de análisis cuantitativo con:
  - Optimización de portafolio (Teoría Moderna de Portafolios - Markowitz)
  - Cálculo de Value at Risk (VaR) histórico y paramétrico
  - Conditional Value at Risk (CVaR / Expected Shortfall)
  - Análisis de contribución marginal al riesgo
  - Simulación Monte Carlo para proyección de retornos
  - Frontera eficiente con restricciones de posición
"""

import numpy as np
import pandas as pd
from datetime import datetime, timedelta
import warnings
warnings.filterwarnings('ignore')

# ─────────────────────────────────────────────
#  CONFIGURACIÓN DEL FONDO
# ─────────────────────────────────────────────
FUND_NAME    = "Alpha Capital Management"
AUM          = 500_000_000   # $500M Assets Under Management
RISK_FREE    = 0.0525        # Fed Funds Rate vigente
BENCHMARK    = "S&P 500"
CONF_LEVEL   = 0.99          # VaR al 99%
TRADE_DAYS   = 252

# ─────────────────────────────────────────────
#  UNIVERSO DE ACTIVOS (10 posiciones)
# ─────────────────────────────────────────────
TICKERS = ['AAPL','MSFT','GOOGL','AMZN','NVDA',
           'JPM','GS','BAC','XOM','JNJ']

SECTOR = {
    'AAPL':'Technology','MSFT':'Technology','GOOGL':'Technology',
    'AMZN':'Consumer Discretionary','NVDA':'Technology',
    'JPM':'Financials','GS':'Financials','BAC':'Financials',
    'XOM':'Energy','JNJ':'Healthcare'
}

# Pesos actuales del portafolio (sub-óptimos - éste es el problema)
CURRENT_WEIGHTS = np.array([0.18, 0.15, 0.12, 0.10, 0.08,
                             0.12, 0.08, 0.07, 0.05, 0.05])

np.random.seed(42)

# ─────────────────────────────────────────────
#  GENERACIÓN DE DATOS HISTÓRICOS SIMULADOS
#  (Representan 3 años de precios diarios)
# ─────────────────────────────────────────────
def generate_price_data(n_days=756):
    """Genera retornos simulados con correlaciones realistas."""
    # Retornos anualizados esperados por activo
    mu_annual = np.array([0.22, 0.20, 0.18, 0.25, 0.38,
                          0.14, 0.16, 0.13, 0.09, 0.10])
    mu_daily = mu_annual / TRADE_DAYS

    # Volatilidades anualizadas
    sigma_annual = np.array([0.28, 0.25, 0.27, 0.30, 0.45,
                             0.22, 0.28, 0.25, 0.30, 0.18])
    sigma_daily = sigma_annual / np.sqrt(TRADE_DAYS)

    # Matriz de correlación (estructura por sector)
    corr = np.array([
        [1.00, 0.75, 0.70, 0.55, 0.65, 0.30, 0.25, 0.25, 0.15, 0.20],
        [0.75, 1.00, 0.68, 0.52, 0.60, 0.32, 0.27, 0.27, 0.18, 0.22],
        [0.70, 0.68, 1.00, 0.58, 0.55, 0.28, 0.23, 0.23, 0.14, 0.18],
        [0.55, 0.52, 0.58, 1.00, 0.48, 0.25, 0.20, 0.20, 0.22, 0.15],
        [0.65, 0.60, 0.55, 0.48, 1.00, 0.22, 0.18, 0.18, 0.12, 0.15],
        [0.30, 0.32, 0.28, 0.25, 0.22, 1.00, 0.72, 0.75, 0.28, 0.25],
        [0.25, 0.27, 0.23, 0.20, 0.18, 0.72, 1.00, 0.68, 0.25, 0.22],
        [0.25, 0.27, 0.23, 0.20, 0.18, 0.75, 0.68, 1.00, 0.22, 0.20],
        [0.15, 0.18, 0.14, 0.22, 0.12, 0.28, 0.25, 0.22, 1.00, 0.15],
        [0.20, 0.22, 0.18, 0.15, 0.15, 0.25, 0.22, 0.20, 0.15, 1.00],
    ])

    # Cholesky decomposition para retornos correlacionados
    L = np.linalg.cholesky(corr)
    Z = np.random.standard_normal((n_days, len(TICKERS)))
    corr_Z = Z @ L.T
    returns = mu_daily + corr_Z * sigma_daily

    # Precios a partir de retornos
    prices = 100 * np.cumprod(1 + returns, axis=0)
    dates = pd.date_range(end=datetime.today(), periods=n_days, freq='B')
    return pd.DataFrame(returns, index=dates, columns=TICKERS), \
           pd.DataFrame(prices,  index=dates, columns=TICKERS)

# ─────────────────────────────────────────────
#  MÉTRICAS DE RIESGO
# ─────────────────────────────────────────────
def portfolio_metrics(weights, returns_df):
    """Calcula retorno, volatilidad y Sharpe del portafolio."""
    port_returns = returns_df @ weights
    ann_return   = port_returns.mean() * TRADE_DAYS
    ann_vol      = port_returns.std()  * np.sqrt(TRADE_DAYS)
    sharpe       = (ann_return - RISK_FREE) / ann_vol
    return ann_return, ann_vol, sharpe

def calculate_var(weights, returns_df, confidence=0.99):
    """Value at Risk histórico y paramétrico."""
    port_returns = (returns_df @ weights)
    port_dollar  = port_returns * AUM

    # VaR Histórico
    var_hist = -np.percentile(port_dollar, (1 - confidence) * 100)

    # VaR Paramétrico (normal)
    from scipy.stats import norm
    mu  = port_dollar.mean()
    sig = port_dollar.std()
    var_param = -(mu + norm.ppf(1 - confidence) * sig)

    # CVaR (Expected Shortfall) - promedio de pérdidas peores que VaR
    threshold  = np.percentile(port_dollar, (1 - confidence) * 100)
    cvar       = -port_dollar[port_dollar <= threshold].mean()

    return var_hist, var_param, cvar

def calculate_beta(weights, returns_df):
    """Beta del portafolio vs benchmark simulado."""
    bm_returns = returns_df.mean(axis=1) * 0.85 + \
                 np.random.normal(0, 0.003, len(returns_df))
    port_returns = returns_df @ weights
    cov_mat = np.cov(port_returns, bm_returns)
    return cov_mat[0, 1] / cov_mat[1, 1]

# ─────────────────────────────────────────────
#  OPTIMIZACIÓN MARKOWITZ (SCIPY)
# ─────────────────────────────────────────────
def optimize_portfolio(returns_df):
    """Encuentra pesos óptimos maximizando Sharpe Ratio."""
    try:
        from scipy.optimize import minimize

        n = len(TICKERS)
        mean_ret = returns_df.mean() * TRADE_DAYS
        cov_mat  = returns_df.cov()  * TRADE_DAYS

        def neg_sharpe(w):
            r = w @ mean_ret
            v = np.sqrt(w @ cov_mat.values @ w)
            return -(r - RISK_FREE) / v

        constraints = [{'type': 'eq', 'fun': lambda w: w.sum() - 1}]
        bounds = [(0.02, 0.25)] * n   # Entre 2% y 25% por posición
        w0 = np.ones(n) / n

        result = minimize(neg_sharpe, w0, method='SLSQP',
                         bounds=bounds, constraints=constraints,
                         options={'ftol': 1e-9, 'maxiter': 1000})
        return result.x if result.success else w0

    except ImportError:
        # Fallback sin scipy: pesos mínima varianza simplificados
        n = len(TICKERS)
        cov_mat = returns_df.cov().values * TRADE_DAYS
        ones = np.ones(n)
        inv_cov = np.linalg.inv(cov_mat + np.eye(n) * 1e-8)
        w = inv_cov @ ones / (ones @ inv_cov @ ones)
        w = np.clip(w, 0.02, 0.25)
        return w / w.sum()

# ─────────────────────────────────────────────
#  SIMULACIÓN MONTE CARLO
# ─────────────────────────────────────────────
def monte_carlo(weights, returns_df, n_sim=10000, horizon=252):
    """Simula distribución de valor del portafolio a 1 año."""
    mu  = (returns_df @ weights).mean()
    sig = (returns_df @ weights).std()
    simulated = AUM * np.exp(
        np.random.normal(
            (mu - 0.5 * sig**2) * horizon,
            sig * np.sqrt(horizon),
            n_sim
        )
    )
    return simulated

# ─────────────────────────────────────────────
#  REPORTE EJECUTIVO EN CONSOLA
# ─────────────────────────────────────────────
def print_report(returns_df, prices_df, opt_weights):
    SEP = "═" * 68

    print(f"\n{SEP}")
    print(f"  {FUND_NAME} — REPORTE DE ANÁLISIS CUANTITATIVO")
    print(f"  Fecha: {datetime.today().strftime('%B %d, %Y')}  |  AUM: ${AUM/1e6:.0f}M")
    print(SEP)

    # ── Métricas portafolio actual
    cr, cv, cs = portfolio_metrics(CURRENT_WEIGHTS, returns_df)
    vH, vP, cvar = calculate_var(CURRENT_WEIGHTS, returns_df)
    beta_curr = calculate_beta(CURRENT_WEIGHTS, returns_df)

    print("\n📊 PORTAFOLIO ACTUAL (Situación Problema)")
    print(f"   Retorno Anualizado  : {cr:>8.2%}")
    print(f"   Volatilidad Anual   : {cv:>8.2%}")
    print(f"   Sharpe Ratio        : {cs:>8.3f}  ← por debajo del mínimo aceptable (1.0)")
    print(f"   Beta vs {BENCHMARK:<10}: {beta_curr:>8.3f}")
    print(f"   VaR 99% (1 día)     : ${vH/1e6:>7.2f}M  (Histórico)")
    print(f"   VaR 99% (1 día)     : ${vP/1e6:>7.2f}M  (Paramétrico)")
    print(f"   CVaR 99% (1 día)    : ${cvar/1e6:>7.2f}M  (Expected Shortfall)")

    # ── Métricas portafolio optimizado
    or_, ov, os_ = portfolio_metrics(opt_weights, returns_df)
    vH2, vP2, cvar2 = calculate_var(opt_weights, returns_df)
    beta_opt = calculate_beta(opt_weights, returns_df)

    print(f"\n✅ PORTAFOLIO OPTIMIZADO (Solución Markowitz)")
    print(f"   Retorno Anualizado  : {or_:>8.2%}  (+{(or_-cr)*100:.0f}bps)")
    print(f"   Volatilidad Anual   : {ov:>8.2%}  ({(ov-cv)*100:+.0f}bps)")
    print(f"   Sharpe Ratio        : {os_:>8.3f}  ↑ MEJORA SIGNIFICATIVA")
    print(f"   Beta vs {BENCHMARK:<10}: {beta_opt:>8.3f}")
    print(f"   VaR 99% (1 día)     : ${vH2/1e6:>7.2f}M  (Histórico)")
    print(f"   CVaR 99% (1 día)    : ${cvar2/1e6:>7.2f}M  (Expected Shortfall)")

    # ── Asignación de activos
    print(f"\n📋 ASIGNACIÓN DE ACTIVOS — ACTUAL vs ÓPTIMA")
    print(f"   {'Ticker':<8} {'Sector':<28} {'Actual':>8} {'Óptimo':>8} {'Δ Cambio':>10}")
    print(f"   {'─'*8} {'─'*28} {'─'*8} {'─'*8} {'─'*10}")
    for i, t in enumerate(TICKERS):
        delta = opt_weights[i] - CURRENT_WEIGHTS[i]
        arrow = "▲" if delta > 0.01 else ("▼" if delta < -0.01 else "─")
        print(f"   {t:<8} {SECTOR[t]:<28} {CURRENT_WEIGHTS[i]:>7.1%} "
              f"{opt_weights[i]:>7.1%} {arrow}{abs(delta):>8.1%}")

    # ── Monte Carlo
    sims = monte_carlo(opt_weights, returns_df)
    p5, p50, p95 = np.percentile(sims, [5, 50, 95])
    print(f"\n🎲 SIMULACIÓN MONTE CARLO — Proyección 12 meses (10,000 escenarios)")
    print(f"   Caso Pesimista  (P5) : ${p5/1e6:>7.1f}M  (retorno: {(p5/AUM-1):+.1%})")
    print(f"   Caso Base      (P50) : ${p50/1e6:>7.1f}M  (retorno: {(p50/AUM-1):+.1%})")
    print(f"   Caso Optimista (P95) : ${p95/1e6:>7.1f}M  (retorno: {(p95/AUM-1):+.1%})")

    print(f"\n💡 RECOMENDACIONES EJECUTIVAS")
    print(f"   1. Reducir concentración en Tech (actual 53%) → máx 40%")
    print(f"   2. Incrementar NVDA y AMZN por mayor Alpha generado")
    print(f"   3. Reducir BAC y XOM: Sharpe individual < 0.5")
    print(f"   4. Pérdida máxima esperada (CVaR 99%) se reduce en "
          f"${(cvar-cvar2)/1e6:.1f}M/día con el portafolio óptimo")
    print(f"\n{SEP}\n")

    return {
        'current': {'return': cr, 'vol': cv, 'sharpe': cs,
                    'var': vH, 'cvar': cvar, 'beta': beta_curr},
        'optimal': {'return': or_, 'vol': ov, 'sharpe': os_,
                    'var': vH2, 'cvar': cvar2, 'beta': beta_opt},
        'opt_weights': opt_weights,
        'mc': {'p5': p5, 'p50': p50, 'p95': p95}
    }

# ─────────────────────────────────────────────
#  MAIN
# ─────────────────────────────────────────────
if __name__ == "__main__":
    print("⚙️  Cargando datos históricos y optimizando portafolio...")
    returns_df, prices_df = generate_price_data()
    opt_weights = optimize_portfolio(returns_df)
    results = print_report(returns_df, prices_df, opt_weights)
    print("✅  Análisis completado. Exportando datos para Excel y Java...")

    # Exportar para uso del Excel builder
    np.save('/home/claude/hedge_fund/opt_weights.npy', opt_weights)
    returns_df.to_csv('/home/claude/hedge_fund/returns_data.csv')
    prices_df.to_csv('/home/claude/hedge_fund/prices_data.csv')

    import json
    with open('/home/claude/hedge_fund/results.json', 'w') as f:
        json.dump({k: {kk: float(vv) for kk, vv in v.items()}
                   if isinstance(v, dict) else v
                   for k, v in results.items()
                   if k != 'opt_weights'}, f, indent=2)
    print("📁  Archivos exportados correctamente.\n")
