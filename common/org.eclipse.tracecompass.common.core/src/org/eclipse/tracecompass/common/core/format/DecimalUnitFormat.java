/*******************************************************************************
 * Copyright (c) 2016 EfficiOS inc, Michael Jeanson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.common.core.format;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;

/**
 * Provides a formatter for decimal numbers with International System of Units
 * suffixes up to peta (quadrillion). It receives a number and formats it in the
 * closest thousand's unit, with at most 1 decimal.
 *
 * @author Michael Jeanson
 * @since 2.0
 */
public class DecimalUnitFormat extends Format {

    private static final long serialVersionUID = 3650332020346870384L;

    /* International System of Units prefixes */
    private static final String K = "k"; //$NON-NLS-1$
    private static final String M = "M"; //$NON-NLS-1$
    private static final String G = "G"; //$NON-NLS-1$
    private static final String T = "T"; //$NON-NLS-1$
    private static final String P = "P"; //$NON-NLS-1$

    private static final long KILO = 1000L;
    private static final long MEGA = 1000000L;
    private static final long GIGA = 1000000000L;
    private static final long TERA = 1000000000000L;
    private static final long PETA = 1000000000000000L;

    private static final Format FORMAT = new DecimalFormat("######.#"); //$NON-NLS-1$

    @Override
    public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
        if (obj instanceof Number) {
            Number num = (Number) obj;
            double value = num.doubleValue();
            double abs = Math.abs(value);
            if (abs >= PETA) {
                return toAppendTo.append(FORMAT.format(value / PETA)).append(' ').append(P);
            }
            if (abs >= TERA) {
                return toAppendTo.append(FORMAT.format(value / TERA)).append(' ').append(T);
            }
            if (abs >= GIGA) {
                return toAppendTo.append(FORMAT.format(value / GIGA)).append(' ').append(G);
            }
            if (abs >= MEGA) {
                return toAppendTo.append(FORMAT.format(value / MEGA)).append(' ').append(M);
            }
            if (abs >= KILO) {
                return toAppendTo.append(FORMAT.format(value / (KILO))).append(' ').append(K);
            }
            return toAppendTo.append(FORMAT.format(value));
        }
        return toAppendTo;
    }

    @Override
    public Object parseObject(String source, ParsePosition pos) {
        return (source == null ? "" : source); //$NON-NLS-1$
    }
}
