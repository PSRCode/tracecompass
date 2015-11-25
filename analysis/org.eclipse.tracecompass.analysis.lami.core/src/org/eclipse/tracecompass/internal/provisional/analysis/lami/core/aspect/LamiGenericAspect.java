/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;

/**
 * Base class for LAMI table aspects.
 *
 * @author Alexandre Montplaisir
 */
public class LamiGenericAspect extends LamiTableEntryAspect {

    private final int fColIndex;
    private final boolean fIsNumerical;
    private final boolean fIsTimeStamp;

    /**
     * Constructor
     *
     * @param aspectName
     *            Name of the aspect (name of the column in the UI)
     * @param units
     *            The units of this column
     * @param colIndex
     *            Index of this column
     * @param isNumerical
     *            If the contents of this column are numbers or not
     * @param isTimeStamp
     *            If the contents of this column are numerical timestamp or not
     */
    public LamiGenericAspect(String aspectName, @Nullable String units, int colIndex, boolean isNumerical, boolean isTimeStamp) {
        super(aspectName, units);
        fColIndex = colIndex;
        fIsNumerical = isNumerical;
        fIsTimeStamp = isTimeStamp;
    }

    @Override
    public boolean isNumerical() {
        return fIsNumerical;
    }

    @Override
    public boolean isTimeStamp() {
        return fIsTimeStamp;
    }

    @Override
    public @Nullable String resolveString(@NonNull LamiTableEntry entry) {
        return entry.getValue(fColIndex).toString();
    }

    @Override
    public double resolveDouble(@NonNull LamiTableEntry entry) {
        if (fIsNumerical) {
            return Double.parseDouble(entry.getValue(fColIndex).toString());
        }
        return 0;
    }

}
