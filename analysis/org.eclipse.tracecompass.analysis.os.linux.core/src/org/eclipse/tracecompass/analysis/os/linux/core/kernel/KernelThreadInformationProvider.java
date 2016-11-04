/*******************************************************************************
 * Copyright (c) 2014, 2015 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Geneviève Bastien - Initial API and implementation
 *******************************************************************************/

package org.eclipse.tracecompass.analysis.os.linux.core.kernel;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.kernel.Attributes;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.statesystem.core.exceptions.AttributeNotFoundException;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue;
import org.eclipse.tracecompass.statesystem.core.statevalue.ITmfStateValue.Type;

/**
 * Information provider utility class that retrieves thread-related information
 * from a Linux Kernel Analysis
 *
 * @author Geneviève Bastien
 * @since 2.0
 */
public final class KernelThreadInformationProvider {

    private KernelThreadInformationProvider() {
    }

    /**
     * Get the ID of the thread running on the CPU at time ts
     *
     * TODO: This method may later be replaced by an aspect, when the aspect can
     * resolve to something that is not an event
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @param cpuId
     *            The CPU number the process is running on
     * @param ts
     *            The timestamp at which we want the running process
     * @return The TID of the thread running on CPU cpuId at time ts or
     *         {@code null} if either no thread is running or we do not know.
     */
    public static @Nullable Integer getThreadOnCpu(KernelAnalysisModule module, long cpuId, long ts) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return null;
        }
        try {
            int cpuQuark = ss.getQuarkAbsolute(Attributes.CPUS, Long.toString(cpuId), Attributes.CURRENT_THREAD);
            ITmfStateInterval interval = ss.querySingleState(ts, cpuQuark);
            ITmfStateValue val = interval.getStateValue();
            if (val.getType().equals(Type.INTEGER)) {
                return val.unboxInt();
            }
        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
        }
        return null;
    }

    /**
     * The the threads that have been scheduled on the given CPU(s), for the
     * given time range.
     *
     * @param module
     *            The kernel analysis module to query
     * @param cpus
     *            The list of cpus
     * @param rangeStart
     *            The start of the time range
     * @param rangeEnd
     *            The end of the time range
     * @return A set of all the thread IDs that are run on said CPUs on the time
     *         range. Empty set if there is no thread on the CPUs in this time
     *         range. Null if the information is not available.
     * @since 2.2
     */
    public static @Nullable Set<Integer> getThreadsOfCpus(KernelAnalysisModule module, List<Long> cpus, long rangeStart, long rangeEnd) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return null;
        }

        Set<Integer> set = new HashSet<>();

        for (Long cpu : cpus) {
            List<ITmfStateInterval> intervals = null;
            try {
                int threadQuark = ss.getQuarkAbsolute(Attributes.CPUS, cpu.toString(), Attributes.CURRENT_THREAD);
                intervals = StateSystemUtils.queryHistoryRange(ss, threadQuark, rangeStart, rangeEnd);
            } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
                continue;
            }

            intervals.stream()
                    .map(interval -> interval.getStateValue())
                    .filter(e -> !e.isNull())
                    .mapToInt(ITmfStateValue::unboxInt)
                    .distinct()
                    .filter(tid -> tid != 0)
                    .forEach(e -> set.add(e));
        }

        return set;
    }

    /**
     * Return all the threads that are considered active in the given time
     * range.
     *
     * @param module
     *            The kernel analysis module to query
     * @param rangeStart
     *            The start of the time range
     * @param rangeEnd
     *            The end of the time range
     * @return A set of all the thread IDs that are considered active in the
     *         time range. Empty set if there are none. Null if the information
     *         is not available.
     * @since 2.2
     */
    public static @Nullable Set<Integer> getActiveThreadsForRange(KernelAnalysisModule module, long rangeStart, long rangeEnd) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return null;
        }

        Set<Integer> activeTids = new HashSet<>();

        try {
            List<ITmfStateInterval> query = ss.queryFullState(rangeStart);
            int threadsQuark = ss.getQuarkAbsolute(Attributes.THREADS);
            List<Integer> threadQuarks = ss.getSubAttributes(threadsQuark, false);

            for (int threadQuark : threadQuarks) {
                int activeStateQuark = ss.getQuarkRelative(threadQuark, Attributes.ACTIVE_STATE);
                ITmfStateInterval activeInterval = query.get(activeStateQuark);

                /* Keep those whose state value is already active at the start of the range */
                if (activeInterval.getStateValue().equals(StateValues.PROCESS_ACTIVE_STATE_ACTIVE)) {
                    String attribName = ss.getAttributeName(threadQuark);
                    /* Ignore swapper threads */
                    if (attribName.startsWith(Attributes.THREAD_0_PREFIX)) {
                        continue;
                    }
                    activeTids.add(Integer.parseInt(attribName));
                    continue;
                }

                /*
                 * For inactive values, keep them only if they hit a state
                 * change during the interval (i.e., if the end time of the
                 * interval is before the rangeEnd).
                 *
                 * Note this will include null -> inactive transitions and vice
                 * versa, which is fine.
                 */
                if (activeInterval.getEndTime() < rangeEnd) {
                    String attribName = ss.getAttributeName(threadQuark);
                    /* Ignore swapper threads */
                    if (attribName.startsWith(Attributes.THREAD_0_PREFIX)) {
                        continue;
                    }
                    activeTids.add(Integer.parseInt(attribName));
                }
            }

        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
            return null;
        }

        return activeTids;
    }

    /**
     * Get the TIDs of the threads from an analysis
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @return The set of TIDs corresponding to the threads
     */
    public static Collection<Integer> getThreadIds(KernelAnalysisModule module) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return Collections.EMPTY_SET;
        }
        int threadQuark;
        try {
            threadQuark = ss.getQuarkAbsolute(Attributes.THREADS);
            Set<@NonNull Integer> tids = new TreeSet<>();
            for (Integer quark : ss.getSubAttributes(threadQuark, false)) {
                final @NonNull String attributeName = ss.getAttributeName(quark);
                tids.add(attributeName.startsWith(Attributes.THREAD_0_PREFIX) ? 0 : Integer.parseInt(attributeName));
            }
            return tids;
        } catch (AttributeNotFoundException e) {
        }
        return Collections.EMPTY_SET;
    }

    /**
     * Get the parent process ID of a thread
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @param threadId
     *            The thread ID of the process for which to get the parent
     * @param ts
     *            The timestamp at which to get the parent
     * @return The parent PID or {@code null} if the PPID is not found.
     */
    public static @Nullable Integer getParentPid(KernelAnalysisModule module, Integer threadId, long ts) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return null;
        }
        Integer ppidNode;
        try {
            ppidNode = ss.getQuarkAbsolute(Attributes.THREADS, threadId.toString(), Attributes.PPID);
            ITmfStateInterval ppidInterval = ss.querySingleState(ts, ppidNode);
            ITmfStateValue ppidValue = ppidInterval.getStateValue();

            if (ppidValue.getType().equals(Type.INTEGER)) {
                return Integer.valueOf(ppidValue.unboxInt());
            }
        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
        }
        return null;
    }

    /**
     * Get the executable name of the thread ID. If the thread ID was used
     * multiple time or the name changed in between, it will return the last
     * name the thread has taken, or {@code null} if no name is found
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @param threadId
     *            The thread ID of the process for which to get the name
     * @return The last executable name of this process, or {@code null} if not
     *         found
     */
    public static @Nullable String getExecutableName(KernelAnalysisModule module, Integer threadId) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return null;
        }
        Integer execNameNode;
        try {
            execNameNode = ss.getQuarkAbsolute(Attributes.THREADS, threadId.toString(), Attributes.EXEC_NAME);
            List<ITmfStateInterval> execNameIntervals = StateSystemUtils.queryHistoryRange(ss, execNameNode, ss.getStartTime(), ss.getCurrentEndTime());

            ITmfStateValue execNameValue;
            String execName = null;
            for (ITmfStateInterval interval : execNameIntervals) {
                execNameValue = interval.getStateValue();
                if (execNameValue.getType().equals(Type.STRING)) {
                    execName = execNameValue.unboxStr();
                }
            }
            return execName;
        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
        }
        return null;
    }

    /**
     * Get the priority of this thread at time ts
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @param threadId
     *            The ID of the thread to query
     * @param ts
     *            The timestamp at which to query
     * @return The priority of the thread or <code>-1</code> if not available
     */
    public static int getThreadPriority(KernelAnalysisModule module, int threadId, long ts) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return -1;
        }
        int prioQuark = ss.optQuarkAbsolute(Attributes.THREADS, String.valueOf(threadId), Attributes.PRIO);
        if (prioQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
            return -1;
        }
        try {
            return ss.querySingleState(ts, prioQuark).getStateValue().unboxInt();
        } catch (StateSystemDisposedException e) {
            return -1;
        }
    }

    /**
     * Get the status intervals for a given thread with a resolution
     *
     * @param module
     *            The kernel analysis instance to run this method on
     * @param threadId
     *            The ID of the thread to get the intervals for
     * @param start
     *            The start time of the requested range
     * @param end
     *            The end time of the requested range
     * @param resolution
     *            The resolution or the minimal time between the requested
     *            intervals. If interval times are smaller than resolution, only
     *            the first interval is returned, the others are ignored.
     * @param monitor
     *            A progress monitor for this task
     * @return The list of status intervals for this thread, an empty list is
     *         returned if either the state system is {@code null} or the quark
     *         is not found
     */
    public static List<ITmfStateInterval> getStatusIntervalsForThread(KernelAnalysisModule module, Integer threadId, long start, long end, long resolution, IProgressMonitor monitor) {
        ITmfStateSystem ss = module.getStateSystem();
        if (ss == null) {
            return Collections.EMPTY_LIST;
        }

        try {
            int threadQuark = ss.getQuarkAbsolute(Attributes.THREADS, threadId.toString());
            List<ITmfStateInterval> statusIntervals = StateSystemUtils.queryHistoryRange(ss, threadQuark, Math.max(start, ss.getStartTime()), Math.min(end - 1, ss.getCurrentEndTime()), resolution, monitor);
            return statusIntervals;
        } catch (AttributeNotFoundException | StateSystemDisposedException | TimeRangeException e) {
        }
        return Collections.EMPTY_LIST;
    }

}
