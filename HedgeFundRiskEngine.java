import java.util.*;
import java.text.DecimalFormat;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║        ALPHA CAPITAL MANAGEMENT — JAVA RISK ENGINE              ║
 * ║        Real-Time Portfolio Risk Calculator v1.0                  ║
 * ║                                                                  ║
 * ║  PURPOSE:                                                        ║
 * ║  Motor de cálculo de riesgo diseñado para ejecutarse en tiempo   ║
 * ║  real dentro de un sistema de gestión de portafolios. Calcula:   ║
 * ║    - Value at Risk (VaR) Paramétrico 99%                         ║
 * ║    - Conditional VaR (Expected Shortfall)                        ║
 * ║    - Sharpe Ratio y métricas de retorno ajustado al riesgo       ║
 * ║    - Beta del portafolio vs benchmark                            ║
 * ║    - Contribución marginal al riesgo por posición                ║
 * ║    - Concentración sectorial y alertas de límites                ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public class HedgeFundRiskEngine {

    // ── Constants ────────────────────────────────────────────────────
    static final double AUM           = 500_000_000.0;
    static final double RISK_FREE     = 0.0525;          // Fed Funds Rate
    static final int    TRADING_DAYS  = 252;
    static final double CONF_LEVEL    = 0.99;
    static final double Z_99          = 2.3263;          // z-score for 99%
    static final double Z_95          = 1.6449;          // z-score for 95%
    static final double MAX_WEIGHT    = 0.25;
    static final double MIN_WEIGHT    = 0.02;
    static final double SECTOR_LIMIT  = 0.45;            // Max sector concentration

    // ── Formatting ───────────────────────────────────────────────────
    static final DecimalFormat PCT  = new DecimalFormat("0.00%");
    static final DecimalFormat DLR  = new DecimalFormat("$#,##0.0M");
    static final DecimalFormat DEC  = new DecimalFormat("0.000");
    static final String SEP         = "═".repeat(68);
    static final String SEP2        = "─".repeat(68);

    // ════════════════════════════════════════════════════════════════
    //  POSITION — represents a single portfolio holding
    // ════════════════════════════════════════════════════════════════
    static class Position {
        String ticker;
        String sector;
        double weight;
        double annReturn;      // Annualized expected return
        double annVolatility;  // Annualized volatility
        double beta;           // vs S&P 500

        Position(String ticker, String sector, double weight,
                 double annReturn, double annVol, double beta) {
            this.ticker       = ticker;
            this.sector       = sector;
            this.weight       = weight;
            this.annReturn    = annReturn;
            this.annVolatility = annVol;
            this.beta         = beta;
        }

        double dollarValue()    { return weight * AUM; }
        double dailyReturn()    { return annReturn / TRADING_DAYS; }
        double dailyVol()       { return annVolatility / Math.sqrt(TRADING_DAYS); }
    }

    // ════════════════════════════════════════════════════════════════
    //  PORTFOLIO — collection of positions + risk methods
    // ════════════════════════════════════════════════════════════════
    static class Portfolio {
        String name;
        List<Position> positions;
        double[][] corrMatrix;   // Correlation matrix

        Portfolio(String name, List<Position> positions, double[][] corr) {
            this.name      = name;
            this.positions = positions;
            this.corrMatrix = corr;
        }

        int size() { return positions.size(); }

        // ── Portfolio-level return (weighted average)
        double portfolioReturn() {
            return positions.stream()
                .mapToDouble(p -> p.weight * p.annReturn)
                .sum();
        }

        // ── Portfolio variance (w'Σw) using correlation decomposition
        double portfolioVariance() {
            int n = size();
            double variance = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    variance += positions.get(i).weight
                              * positions.get(j).weight
                              * positions.get(i).annVolatility
                              * positions.get(j).annVolatility
                              * corrMatrix[i][j];
                }
            }
            return variance;
        }

        double portfolioVolatility() { return Math.sqrt(portfolioVariance()); }

        // ── Sharpe Ratio
        double sharpeRatio() {
            return (portfolioReturn() - RISK_FREE) / portfolioVolatility();
        }

        // ── Portfolio Beta (weighted average of individual betas)
        double portfolioBeta() {
            return positions.stream()
                .mapToDouble(p -> p.weight * p.beta)
                .sum();
        }

        // ── VaR Paramétrico (1-day)
        double varParametric(double zScore) {
            double dailyVol = portfolioVolatility() / Math.sqrt(TRADING_DAYS);
            double dailyRet = portfolioReturn() / TRADING_DAYS;
            return -(dailyRet - zScore * dailyVol) * AUM;
        }

        // ── CVaR / Expected Shortfall (approximation under normality)
        // ES = μ - σ × φ(z) / (1-α)  where φ(z) is standard normal PDF
        double cvar99() {
            double phi_z = Math.exp(-0.5 * Z_99 * Z_99) / Math.sqrt(2 * Math.PI);
            double dailyVol = portfolioVolatility() / Math.sqrt(TRADING_DAYS);
            double dailyRet = portfolioReturn() / TRADING_DAYS;
            return -(dailyRet - dailyVol * phi_z / 0.01) * AUM;
        }

        // ── Marginal Risk Contribution per position (∂σ/∂wᵢ × wᵢ / σ)
        double[] marginalRiskContribution() {
            int n = size();
            double[] mrc = new double[n];
            double portVol = portfolioVolatility();
            for (int i = 0; i < n; i++) {
                double covWithPort = 0.0;
                for (int j = 0; j < n; j++) {
                    covWithPort += positions.get(j).weight
                                 * positions.get(i).annVolatility
                                 * positions.get(j).annVolatility
                                 * corrMatrix[i][j];
                }
                mrc[i] = (positions.get(i).weight * covWithPort) / portVol;
            }
            return mrc;
        }

        // ── Sector concentrations
        Map<String, Double> sectorConcentration() {
            Map<String, Double> sectors = new LinkedHashMap<>();
            for (Position p : positions) {
                sectors.merge(p.sector, p.weight, Double::sum);
            }
            return sectors;
        }

        // ── Validate constraints
        List<String> validateConstraints() {
            List<String> alerts = new ArrayList<>();
            for (Position p : positions) {
                if (p.weight > MAX_WEIGHT)
                    alerts.add("⚠ OVERWEIGHT: " + p.ticker +
                               " at " + PCT.format(p.weight) +
                               " exceeds " + PCT.format(MAX_WEIGHT) + " limit");
                if (p.weight < MIN_WEIGHT)
                    alerts.add("⚠ UNDERWEIGHT: " + p.ticker +
                               " at " + PCT.format(p.weight) +
                               " below " + PCT.format(MIN_WEIGHT) + " minimum");
            }
            sectorConcentration().forEach((sector, wt) -> {
                if (wt > SECTOR_LIMIT)
                    alerts.add("⚠ SECTOR LIMIT: " + sector +
                               " at " + PCT.format(wt) +
                               " exceeds " + PCT.format(SECTOR_LIMIT) + " cap");
            });
            double totalWeight = positions.stream().mapToDouble(p -> p.weight).sum();
            if (Math.abs(totalWeight - 1.0) > 0.001)
                alerts.add("⚠ WEIGHTS SUM: " + PCT.format(totalWeight) +
                           " (must equal 100%)");
            return alerts;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  REPORT PRINTER
    // ════════════════════════════════════════════════════════════════
    static void printReport(Portfolio current, Portfolio optimal) {
        System.out.println("\n" + SEP);
        System.out.println("  ALPHA CAPITAL MANAGEMENT — JAVA RISK ENGINE v1.0");
        System.out.println("  Real-Time Portfolio Risk Calculator");
        System.out.println("  AUM: " + DLR.format(AUM / 1e6) +
                           "  |  Risk-Free: " + PCT.format(RISK_FREE) +
                           "  |  Confidence: " + PCT.format(CONF_LEVEL));
        System.out.println(SEP);

        // ── Side-by-side comparison
        System.out.println("\n  PORTFOLIO PERFORMANCE SUMMARY");
        System.out.println(SEP2);
        System.out.printf("  %-28s  %-16s  %-16s%n",
            "METRIC", "CURRENT PORT.", "OPTIMAL PORT.");
        System.out.println(SEP2);

        String[][] rows = {
            {"Annualized Return",
             PCT.format(current.portfolioReturn()),
             PCT.format(optimal.portfolioReturn())},
            {"Annualized Volatility",
             PCT.format(current.portfolioVolatility()),
             PCT.format(optimal.portfolioVolatility())},
            {"Sharpe Ratio",
             DEC.format(current.sharpeRatio()),
             DEC.format(optimal.sharpeRatio())},
            {"Portfolio Beta",
             DEC.format(current.portfolioBeta()),
             DEC.format(optimal.portfolioBeta())},
            {"VaR 99% (1-day)",
             DLR.format(current.varParametric(Z_99) / 1e6),
             DLR.format(optimal.varParametric(Z_99) / 1e6)},
            {"VaR 95% (1-day)",
             DLR.format(current.varParametric(Z_95) / 1e6),
             DLR.format(optimal.varParametric(Z_95) / 1e6)},
            {"CVaR 99% (Exp. Shortfall)",
             DLR.format(current.cvar99() / 1e6),
             DLR.format(optimal.cvar99() / 1e6)},
        };
        for (String[] row : rows) {
            // Highlight improvement
            String flag = "";
            if (row[0].contains("Sharpe") &&
                Double.parseDouble(row[2]) > Double.parseDouble(row[1])) flag = " ↑";
            System.out.printf("  %-28s  %-16s  %-16s%s%n",
                row[0], row[1], row[2], flag);
        }

        // ── Marginal Risk Contribution
        System.out.println("\n  MARGINAL RISK CONTRIBUTION — CURRENT PORTFOLIO");
        System.out.println(SEP2);
        System.out.printf("  %-8s  %-26s  %-10s  %-10s  %-12s%n",
            "TICKER", "SECTOR", "WEIGHT", "MRC", "% OF RISK");
        System.out.println(SEP2);
        double[] mrc = current.marginalRiskContribution();
        double totalRisk = Arrays.stream(mrc).sum();
        for (int i = 0; i < current.size(); i++) {
            Position p = current.positions.get(i);
            System.out.printf("  %-8s  %-26s  %-10s  %-10s  %-12s%n",
                p.ticker,
                p.sector,
                PCT.format(p.weight),
                String.format("%.4f", mrc[i]),
                PCT.format(mrc[i] / totalRisk));
        }

        // ── Sector Concentration
        System.out.println("\n  SECTOR CONCENTRATION ANALYSIS");
        System.out.println(SEP2);
        System.out.printf("  %-28s  %-12s  %-12s  %-12s%n",
            "SECTOR", "CURRENT", "OPTIMAL", "LIMIT");
        System.out.println(SEP2);
        Map<String,Double> curSec = current.sectorConcentration();
        Map<String,Double> optSec = optimal.sectorConcentration();
        for (String s : curSec.keySet()) {
            double cw = curSec.getOrDefault(s, 0.0);
            double ow = optSec.getOrDefault(s, 0.0);
            String flag = cw > SECTOR_LIMIT ? "  ⚠ BREACH" : "";
            System.out.printf("  %-28s  %-12s  %-12s  %-12s%s%n",
                s,
                PCT.format(cw),
                PCT.format(ow),
                PCT.format(SECTOR_LIMIT),
                flag);
        }

        // ── Constraint Validation
        List<String> curAlerts = current.validateConstraints();
        List<String> optAlerts = optimal.validateConstraints();
        System.out.println("\n  CONSTRAINT VALIDATION");
        System.out.println(SEP2);
        System.out.println("  Current Portfolio:");
        if (curAlerts.isEmpty())
            System.out.println("   ✔ All constraints satisfied");
        else curAlerts.forEach(a -> System.out.println("   " + a));
        System.out.println("  Optimal Portfolio:");
        if (optAlerts.isEmpty())
            System.out.println("   ✔ All constraints satisfied");
        else optAlerts.forEach(a -> System.out.println("   " + a));

        // ── Executive Recommendation
        double sharpeImprovement =
            (optimal.sharpeRatio() - current.sharpeRatio()) / Math.abs(current.sharpeRatio());
        double varReduction =
            (current.varParametric(Z_99) - optimal.varParametric(Z_99)) /
            current.varParametric(Z_99);

        System.out.println("\n  EXECUTIVE RECOMMENDATION");
        System.out.println(SEP2);
        System.out.printf("  Sharpe Ratio Improvement : %s (+%.1f%%)%n",
            DEC.format(optimal.sharpeRatio()), sharpeImprovement * 100);
        System.out.printf("  VaR Reduction            : %s saved/day%n",
            DLR.format((current.varParametric(Z_99) -
                        optimal.varParametric(Z_99)) / 1e6));
        System.out.println("  Action Required          : Rebalance portfolio per");
        System.out.println("                             Markowitz optimal weights.");
        System.out.println("                             Tech sector over SECTOR_LIMIT.");
        System.out.println(SEP + "\n");
    }

    // ════════════════════════════════════════════════════════════════
    //  MAIN — Build portfolios and run risk report
    // ════════════════════════════════════════════════════════════════
    public static void main(String[] args) {

        // ── Correlation matrix (10×10, symmetric)
        double[][] corr = {
            {1.00,0.75,0.70,0.55,0.65,0.30,0.25,0.25,0.15,0.20},
            {0.75,1.00,0.68,0.52,0.60,0.32,0.27,0.27,0.18,0.22},
            {0.70,0.68,1.00,0.58,0.55,0.28,0.23,0.23,0.14,0.18},
            {0.55,0.52,0.58,1.00,0.48,0.25,0.20,0.20,0.22,0.15},
            {0.65,0.60,0.55,0.48,1.00,0.22,0.18,0.18,0.12,0.15},
            {0.30,0.32,0.28,0.25,0.22,1.00,0.72,0.75,0.28,0.25},
            {0.25,0.27,0.23,0.20,0.18,0.72,1.00,0.68,0.25,0.22},
            {0.25,0.27,0.23,0.20,0.18,0.75,0.68,1.00,0.22,0.20},
            {0.15,0.18,0.14,0.22,0.12,0.28,0.25,0.22,1.00,0.15},
            {0.20,0.22,0.18,0.15,0.15,0.25,0.22,0.20,0.15,1.00}
        };

        // ── CURRENT PORTFOLIO (sub-optimal — the "problem")
        List<Position> curPositions = Arrays.asList(
            new Position("AAPL", "Technology",             0.18, 0.22, 0.28, 1.21),
            new Position("MSFT", "Technology",             0.15, 0.20, 0.25, 1.15),
            new Position("GOOGL","Technology",             0.12, 0.18, 0.27, 1.12),
            new Position("AMZN", "Consumer Discretionary", 0.10, 0.25, 0.30, 1.28),
            new Position("NVDA", "Technology",             0.08, 0.38, 0.45, 1.55),
            new Position("JPM",  "Financials",             0.12, 0.14, 0.22, 1.05),
            new Position("GS",   "Financials",             0.08, 0.16, 0.28, 1.18),
            new Position("BAC",  "Financials",             0.07, 0.13, 0.25, 1.10),
            new Position("XOM",  "Energy",                 0.05, 0.09, 0.30, 0.78),
            new Position("JNJ",  "Healthcare",             0.05, 0.10, 0.18, 0.62)
        );

        // ── OPTIMAL PORTFOLIO (Markowitz solution from Python engine)
        List<Position> optPositions = Arrays.asList(
            new Position("AAPL", "Technology",             0.250, 0.22, 0.28, 1.21),
            new Position("MSFT", "Technology",             0.250, 0.20, 0.25, 1.15),
            new Position("GOOGL","Technology",             0.047, 0.18, 0.27, 1.12),
            new Position("AMZN", "Consumer Discretionary", 0.020, 0.25, 0.30, 1.28),
            new Position("NVDA", "Technology",             0.103, 0.38, 0.45, 1.55),
            new Position("JPM",  "Financials",             0.020, 0.14, 0.22, 1.05),
            new Position("GS",   "Financials",             0.020, 0.16, 0.28, 1.18),
            new Position("BAC",  "Financials",             0.020, 0.13, 0.25, 1.10),
            new Position("XOM",  "Energy",                 0.020, 0.09, 0.30, 0.78),
            new Position("JNJ",  "Healthcare",             0.250, 0.10, 0.18, 0.62)
        );

        Portfolio current = new Portfolio("Current Portfolio", curPositions, corr);
        Portfolio optimal = new Portfolio("Optimal Portfolio", optPositions, corr);

        printReport(current, optimal);
    }
}
