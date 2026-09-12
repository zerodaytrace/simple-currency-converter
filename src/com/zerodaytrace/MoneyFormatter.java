package com.zerodaytrace;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Issue #6: the display parameters used to be magic numbers scattered through
 * {@link CurrencyMain}. Gathering them into one immutable value type makes the formatting
 * configurable in a single place and unit-testable, while {@link #DEFAULT} preserves the
 * application's existing output exactly.
 */
public final class MoneyFormatter {

    private final int decimalPlaces;
    private final int significantFigures;
    private final BigDecimal subCentThreshold;

    /**
     * @param decimalPlaces      fixed fraction digits for "normal" amounts
     * @param significantFigures precision used for rates and for sub-threshold amounts
     * @param subCentThreshold   amounts smaller than this (in magnitude) fall back to
     *                           significant-figure formatting so they don't collapse to "0.00"
     */
    public MoneyFormatter(int decimalPlaces, int significantFigures, BigDecimal subCentThreshold) {
        this.decimalPlaces = decimalPlaces;
        this.significantFigures = significantFigures;
        this.subCentThreshold = subCentThreshold;
    }

    /** The configuration that reproduces the behaviour hardcoded before issue #6. */
    public static final MoneyFormatter DEFAULT =
            new MoneyFormatter(2, 6, new BigDecimal("0.005"));

    public String formatAmount(double value) {
        return formatAmount(BigDecimal.valueOf(value));
    }

    // BigDecimal overload so the converted amount (BigDecimal since issue #2) keeps its full
    // precision all the way to display; the double overload delegates here.
    public String formatAmount(BigDecimal value) {
        if (value.signum() != 0 && value.abs().compareTo(subCentThreshold) < 0) {
            return toSignificant(value);
        }
        return value.setScale(decimalPlaces, RoundingMode.HALF_UP).toPlainString();
    }

    public String formatRate(double rate) {
        return toSignificant(BigDecimal.valueOf(rate));
    }

    private String toSignificant(BigDecimal value) {
        return value.round(new MathContext(significantFigures))
                .stripTrailingZeros()
                .toPlainString();
    }
}
