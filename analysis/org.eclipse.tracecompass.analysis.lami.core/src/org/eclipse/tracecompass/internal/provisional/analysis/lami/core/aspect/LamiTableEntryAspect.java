/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;

/**
 * Aspect for Babeltrace table entries, which normally correspond to one "row"
 * of JSON output.
 *
 * It is not the same as a "Event aspect" used for trace events, but it is
 * heavily inspired from it.
 *
 * @author Alexandre Montplaisir
 * @see org.eclipse.tracecompass.tmf.core.event.aspect.ITmfEventAspect
 */
public abstract class LamiTableEntryAspect {

    private final String fName;
    private final @Nullable String fUnits;

    /**
     * Constructor
     *
     * @param name
     *            Aspect name, will be used as column name in the UI
     * @param units
     *            The units of the value in this column
     */
    protected LamiTableEntryAspect(String name, @Nullable String units) {
        fUnits = units;

        if (fUnits == null) {
            fName = name;
        } else {
            fName = (name + " (" + fUnits + ')'); //$NON-NLS-1$
        }
    }

    /**
     * Get the name of this aspect.
     *
     * @return The name
     */
    public String getName() {
        return fName;
    }

    /**
     * Get the units of this aspect.
     *
     * @return The name
     */
    public @Nullable String getUnits() {
        return fUnits;
    }

    /**
     * Indicate if this aspect is numerical or not. This is used, among other
     * things, to align the text in the table cells.
     *
     * @return If this aspect is numerical or not
     */
    public abstract boolean isNumerical();


    /**
     * Indicate if this aspect represent timestamp or not. This can be used in chart
     * for axis labeling etc.
     * @return  If this aspect represent a timestamp or not
     */
    public abstract boolean isTimeStamp();

    /**
     * Resolve this aspect for the given entry.
     *
     * @param entry
     *            The table row
     * @return The string to display for the given cell
     */
    public abstract @Nullable String resolveString(LamiTableEntry entry);

    /**
     * Resolve this aspect double representation for the given entry
     *
     * Returned value does not matter if isNumerical() is false.
     *
     * @param entry
     *            The table row
     * @return The double value for the given cell
     */
    public abstract double resolveDouble(LamiTableEntry entry);
}
