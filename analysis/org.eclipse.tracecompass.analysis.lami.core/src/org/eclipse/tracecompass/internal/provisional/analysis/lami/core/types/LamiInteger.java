/*******************************************************************************
 * Copyright (c) 2015 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types;

/**
 * Integer data element
 *
 * @author Alexandre Montplaisir
 */
public class LamiInteger extends LamiData {

    private final long fValue;

    /**
     * Constructor
     *
     * @param value The integer value (as a long)
     */
    public LamiInteger(long value) {
        fValue = value;
    }

    /**
     * Return the value
     *
     * @return The value
     */
    public long getValue() {
        return fValue;
    }

    @Override
    public String toString() {
        return String.valueOf(fValue);
    }
}
