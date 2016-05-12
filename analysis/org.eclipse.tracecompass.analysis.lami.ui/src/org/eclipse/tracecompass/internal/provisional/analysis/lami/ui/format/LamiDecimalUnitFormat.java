/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Jonathan Rajotte-Julien
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.format;

import java.math.BigDecimal;
import java.text.FieldPosition;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.format.DecimalUnitFormat;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.viewers.LamiGraphRange;

/**
 *
 * Decimal formatter for Lami graph
 *
 * Since the graph use normalized internal value the initial (external) representation
 * needs to be obtained. Subsequent formatting is done based on a Double. Loss
 * of precision could occurs based on the size. For now, loss of precision for
 * decimal values is not a big concern. If it ever become one the use of Long
 * while formatting might come in handy.
 *
 * @author Jonathan Rajotte-Julien
 */
public class LamiDecimalUnitFormat extends DecimalUnitFormat {

    private static final long serialVersionUID = 977671266270661188L;

    private @Nullable LamiGraphRange fInternalRange = null;
    private @Nullable LamiGraphRange fExternalRange = null;

    /**
     * Default constructor
     */
    public LamiDecimalUnitFormat() {
        super();
    }

    /**
     * Constructor with internal and external LamiRange for scale transformation
     *
     * @param internalRange The internal range used for graph representation
     *
     * @param externalRange
     *            The external (real value) range shown to the user
     */
    public LamiDecimalUnitFormat(LamiGraphRange internalRange, LamiGraphRange externalRange) {
        super();
        fInternalRange = internalRange;
        fExternalRange = externalRange;
    }

    /**
     * Constructor with multiplication factor.
     *
     * @param factor
     *            Multiplication factor to apply to the value
     */
    public LamiDecimalUnitFormat(double factor) {
        super(factor);
    }

    /**
     * Constructor with multiplication factor and internal and external
     * LamiRange for scale transformation.
     *
     * @param factor
     *            Multiplication factor to apply to the value
     * @param internalRange
     *            The internal range used for graph representation
     * @param externalRange
     *            The external (real value) range shown to the user
     */
    public LamiDecimalUnitFormat(double factor, LamiGraphRange internalRange, LamiGraphRange externalRange) {
        super(factor);
        fInternalRange = internalRange;
        fExternalRange = externalRange;
    }

    /**
     * @return the internal range definition
     */
    public @Nullable LamiGraphRange getInternalRange() {
        return fInternalRange;
    }

    /**
     * @param internalRange the internal range definition to be used by the formatter
     */
    public void setInternalRange(@Nullable LamiGraphRange internalRange) {
        fInternalRange = internalRange;
    }

    /**
     * @return the external range definition
     */
    public @Nullable LamiGraphRange getExternalRange() {
        return fExternalRange;
    }

    /**
     * @param externalRange the external range definition to be used by the formatter
     */
    public void setExternalRange(@Nullable LamiGraphRange externalRange) {
        fExternalRange = externalRange;
    }

    @Override
    public StringBuffer format(@Nullable Object obj, @Nullable StringBuffer toAppendTo, @Nullable FieldPosition pos) {
        if (obj != null && obj instanceof Number && toAppendTo != null) {
            @Nullable LamiGraphRange internalRange = fInternalRange;
            @Nullable LamiGraphRange externalRange = fExternalRange;
            if (internalRange == null || externalRange == null) {
                StringBuffer buffer = super.format(obj, toAppendTo, pos);
                return buffer == null ? new StringBuffer() : buffer;
            }

            if (internalRange.getRangeDelta().compareTo(BigDecimal.ZERO) == 0) {
                StringBuffer buffer = super.format(externalRange.getMinimum().doubleValue(), toAppendTo, pos);
                return buffer == null ? new StringBuffer() : buffer;
            }


            BigDecimal range = externalRange.getRangeDelta();
            if (range.compareTo(BigDecimal.ZERO) == 0) {
                range = BigDecimal.ONE;
            }

            /* Find external value before formatting */
            BigDecimal externalValue = (new BigDecimal(obj.toString()))
                    .subtract(internalRange.getMinimum())
                    .multiply(range)
                    .divide(internalRange.getRangeDelta(), 22, BigDecimal.ROUND_DOWN)
                    .add(externalRange.getMinimum());

            Double value = externalValue.doubleValue();
            StringBuffer buffer = super.format(value, toAppendTo, pos);
            return buffer == null ? new StringBuffer() : buffer;
        }

        throw new IllegalArgumentException("Cannot format given Object as a Number: " + obj); //$NON-NLS-1$
    }
}
