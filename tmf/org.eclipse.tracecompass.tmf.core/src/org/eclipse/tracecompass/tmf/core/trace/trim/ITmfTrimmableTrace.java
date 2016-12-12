/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.core.trace.trim;

import java.nio.file.Path;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;

/**
 * Interface to augment {@link org.eclipse.tracecompass.tmf.core.trace.ITmfTrace}
 * implementations that offer trimming capabilities. This means creating a copy
 * of the trace that contains only the events in a given time range.
 *
 * @author Alexandre Montplaisir
 * @since 2.3
 */
public interface ITmfTrimmableTrace {

    /**
     * Perform trim operation on the current trace, keeping only the area
     * overlapping the passed time range. The new trace will be created in the
     * destination path.
     *
     * @param range
     *            The time range outside of which to trim. Will be clamped to
     *            the original trace's own time range.
     * @param destinationPath
     *            The location where the new trace will be created.
     * @param monitor
     *            Progress monitor for cases where the operation is ran from
     *            inside a Job. You can use a
     *            {@link org.eclipse.core.runtime.NullProgressMonitor} if none
     *            is available.
     * @throws CoreException
     *             Optional exception indicating an error during the execution
     *             of the operation. Will be reported to the user inside an
     *             error dialog.
     */
    void trim(TmfTimeRange range, Path destinationPath, IProgressMonitor monitor) throws CoreException;

}
