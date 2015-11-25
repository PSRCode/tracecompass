/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.analysis.ondemand;

/**
 * Reports are the output of on-demand analysis.
 *
 * @author Alexandre Montplaisir
 * @since 2.0
 */
public interface IOndemandAnalysisReport {

    /**
     * Get the name of this report.
     *
     * @return The name of this report
     */
    String getName();
}
