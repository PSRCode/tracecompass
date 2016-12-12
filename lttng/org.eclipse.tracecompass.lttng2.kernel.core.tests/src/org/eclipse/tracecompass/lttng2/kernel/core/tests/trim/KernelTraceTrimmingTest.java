/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.lttng2.kernel.core.tests.trim;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.analysis.os.linux.core.kernel.KernelAnalysisModule;
import org.eclipse.tracecompass.lttng2.kernel.core.trace.LttngKernelTrace;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.Statedump;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.testtraces.ctf.CtfTestTrace;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceOpenedSignal;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ctf.core.event.CtfTmfEvent;
import org.eclipse.tracecompass.tmf.ctf.core.tests.shared.CtfTmfTestTraceUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

/**
 * Test of the trace trimming feature on an LTTng kernel trace.
 *
 * @author Alexandre Montplaisir
 */
public class KernelTraceTrimmingTest {

    /** Test timeout */
    @Rule public TestRule globalTimeout= new Timeout(5, TimeUnit.MINUTES);

    private static final @NonNull CtfTestTrace KERNEL_TRACE = CtfTestTrace.KERNEL;

    private LttngKernelTrace fKernelTrace;

    /**
     * Test setup
     */
    @Before
    public void setup() {
        /*
         * We need to initialize the kernel trace to the "LttngKernelTrace"
         * type ourselves, so the kernel analysis can run on it.
         */
        String kernelTracePath = CtfTmfTestTraceUtils.getTrace(KERNEL_TRACE).getPath();
        fKernelTrace = openTrace(kernelTracePath);
    }

    /**
     * Test teardown
     */
    @After
    public void tearDown() {
        if (fKernelTrace != null) {
            fKernelTrace.dispose();
        }

        CtfTmfTestTraceUtils.dispose(KERNEL_TRACE);
    }

    private static @NonNull LttngKernelTrace openTrace(String tracePath) {
        LttngKernelTrace trace = new LttngKernelTrace();

        try {
            trace.initTrace(null, tracePath, CtfTmfEvent.class);
        } catch (TmfTraceException e) {
            fail(e.getMessage());
        }

        /* Simulate the trace being opened */
        TmfSignalManager.dispatchSignal(new TmfTraceOpenedSignal(KernelTraceTrimmingTest.class, trace, null));

        return trace;
    }

    private static KernelAnalysisModule getKernelModule(@NonNull LttngKernelTrace trace) {
        KernelAnalysisModule module = TmfTraceUtils.getAnalysisModuleOfClass(trace,
                KernelAnalysisModule.class, KernelAnalysisModule.ID);
        assertNotNull(module);
        module.waitForCompletion();
        return module;
    }


    /**
     * Test the trim() command.
     *
     * @throws Exception
     *             If something fails
     */
    @Test
    public void testTrim() throws Exception {
        LttngKernelTrace initialTrace = fKernelTrace;
        assertNotNull(initialTrace);

        // TODO
        final long startTime = initialTrace.getStartTime().toNanos() + 100000;
        final long endTime = startTime + 100000;

        KernelAnalysisModule module = getKernelModule(initialTrace);
        int providerVersion = checkNotNull(module.getProviderVersion());
        ITmfStateSystem ss1 = module.getStateSystem();
        assertNotNull(ss1);
        List<ITmfStateInterval> state1 = ss1.queryFullState(startTime + 1);

        TmfTimeRange range = new TmfTimeRange(TmfTimestamp.fromNanos(startTime), TmfTimestamp.fromNanos(endTime));

        Path path = null;
        try {
            path = checkNotNull(Files.createTempDirectory("trimmed-trace-test"));

            /* Trim and create the new trace */
            initialTrace.trim(range, path, new NullProgressMonitor());

            /* Write the statedump */
            Statedump statedump = new Statedump(ss1, startTime, providerVersion);
            statedump.dumpState(path, ss1.getSSID());

            /* Open the new trace, then sure the statedump was loaded correctly */
            LttngKernelTrace trimmedTrace = openTrace(path.toString());
            ITmfStateSystem ss2 = getKernelModule(trimmedTrace).getStateSystem();
            assertNotNull(ss2);
            List<ITmfStateInterval> state2 = ss2.queryFullState(startTime + 1);

            assertEquals(state1.size(), state2.size());
            for (int ss1quark = 0; ss1quark < state1.size(); ss1quark++) {
                /*
                 * The quarks are not necessarily the same between the old and
                 * new state systems! We have to resolve new quarks from the
                 * full paths.
                 */
                String[] attributePath = ss1.getFullAttributePathArray(ss1quark);
                int ss2quark = ss2.getQuarkAbsolute(attributePath);

                ITmfStateInterval interval1 = state1.get(ss1quark);
                ITmfStateInterval interval2 = state2.get(ss2quark);
                assertEquals(interval1.getStartTime(), interval2.getStartTime());
                assertEquals(interval1.getEndTime(), interval2.getEndTime());
                assertEquals(interval1.getStateValue(), interval2.getStateValue());
            }

        } finally {
            if (path != null) {
                FileUtils.deleteQuietly(path.toFile());
            }
        }

    }
}
