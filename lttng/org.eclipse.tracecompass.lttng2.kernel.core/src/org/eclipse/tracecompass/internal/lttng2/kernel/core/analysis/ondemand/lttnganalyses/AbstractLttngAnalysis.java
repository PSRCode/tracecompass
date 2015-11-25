/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.lttng2.kernel.core.analysis.ondemand.lttnganalyses;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.trace.IKernelAnalysisEventLayout;
import org.eclipse.tracecompass.internal.lttng2.kernel.core.trace.layout.Lttng27EventLayout;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiAnalysis;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;

/**
 * Base class for all LTTng-Analyses wrappers.
 *
 * @author Alexandre Montplaisir
 */
public abstract class AbstractLttngAnalysis extends LamiAnalysis {

    /**
     * Constructor, meant to be called by sub-classe
     *
     * @param scriptExecutable
     *            The executable to run
     * @param parameters
     *            The script's parameters
     */
    protected AbstractLttngAnalysis(String scriptExecutable, @NonNull String... parameters) {
        super(scriptExecutable, parameters);
    }

    @Override
    public abstract String getName();

    @Override
    protected Multimap<String, LamiChartModel> getPredefinedViews() {
        /* No predefined views by default, subclasses can override */
        return ImmutableMultimap.of();
    }

    @Override
    public boolean appliesTo(ITmfTrace trace) {
        /* LTTng-Analysis is supported only on LTTng >= 2.7 kernel traces */
        if (trace instanceof LttngKernelTrace) {
            LttngKernelTrace kernelTrace = (LttngKernelTrace) trace;
            IKernelAnalysisEventLayout layout = kernelTrace.getKernelEventLayout();
            if (layout instanceof Lttng27EventLayout) {
                return true;
            }
        }
        return false;
    }
}
