/*******************************************************************************
 * Copyright (c) 2015, 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect;

import java.util.Comparator;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types.LamiData;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types.LamiTimeRange;

/**
 * Aspect for a time range duration
 *
 * @author Alexandre Montplaisir
 */
public class LamiTimeRangeDurationAspect extends LamiTableEntryAspect {

    private final int fColIndex;

    /**
     * Constructor
     *
     * @param timeRangeName
     *            Name of the time range
     * @param colIndex
     *            Column index
     */
    public LamiTimeRangeDurationAspect(String timeRangeName, int colIndex) {
        super(timeRangeName + " (" + Messages.LamiAspect_TimeRangeDuration + ')', "ns"); //$NON-NLS-1$ //$NON-NLS-2$
        fColIndex = colIndex;
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean isTimeStamp() {
        return false;
    }

    @Override
    public boolean isTimeDuration() {
        return true;
    }

    @Override
    public @Nullable String resolveString(LamiTableEntry entry) {
        LamiData data = entry.getValue(fColIndex);
        if (data instanceof LamiTimeRange) {
            LamiTimeRange range = (LamiTimeRange) data;
            return String.valueOf(range.getDuration());
        }
        return data.toString();
    }

    @Override
    public @Nullable Number resolveNumber(@NonNull LamiTableEntry entry) {
        LamiData data = entry.getValue(fColIndex);
        if (data instanceof LamiTimeRange) {
            LamiTimeRange range = (LamiTimeRange) data;
            return Long.valueOf(range.getDuration());
        }
        return null;
    }

    @Override
    public Comparator<LamiTableEntry> getComparator() {
        return (o1, o2) -> {
            Number dO1 = resolveNumber(o1);
            Number dO2 = resolveNumber(o2);
            if (dO1 == null || dO2 == null) {
                return 0;
            }

            if (!dO1.getClass().equals(dO2.getClass())) {
                return 0;
            }

            if (!(dO1 instanceof Long && dO2 instanceof Long)) {
                return 0;
            }

            return ((Long)dO1).compareTo((Long)dO2);
        };
    }

}
