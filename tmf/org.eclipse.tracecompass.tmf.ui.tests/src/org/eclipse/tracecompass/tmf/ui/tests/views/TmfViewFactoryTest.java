/**********************************************************************
 * Copyright (c) 2016 EfficiOS Inc.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/
package org.eclipse.tracecompass.tmf.ui.tests.views;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.eclipse.tracecompass.tmf.ui.tests.stubs.views.TmfViewStub;
import org.eclipse.tracecompass.tmf.ui.views.TmfViewFactory;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.junit.Test;

/**
 * @author Jonathan Rajotte Julien
 */
public class TmfViewFactoryTest {

    /* The internal separator */
    private static final String separator = TmfViewFactory.INTERNAL_SECONDARY_ID_SEPARATOR;
    private static final String uuid = "90148656-2bd7-4f3e-8513-4def0ac76b1f";

    BaseSecIdTestCase[] baseSecIdTestCases = {
            new BaseSecIdTestCase(null, null),
            new BaseSecIdTestCase(uuid, null),
            new BaseSecIdTestCase(separator + uuid, ""),
            new BaseSecIdTestCase(separator + "1-1-1-1-1", separator+ "1-1-1-1-1"),
            new BaseSecIdTestCase("sec_id", "sec_id"),
            new BaseSecIdTestCase("sec_id" + separator, "sec_id" + separator),
            new BaseSecIdTestCase("sec_id" + separator + uuid, "sec_id"),
            new BaseSecIdTestCase("sec_id" + separator + separator + uuid, "sec_id" + separator),
            new BaseSecIdTestCase("sec_id" + separator + "third_id", "sec_id" + separator + "third_id"),
            new BaseSecIdTestCase("sec_id" + separator + "third_id" + separator + uuid, "sec_id" + separator + "third_id"),
            new BaseSecIdTestCase("sec_id" + separator + "third_id" + separator + "fourth_id", "sec_id" + separator + "third_id" + separator + "fourth_id"),
    };

    class BaseSecIdTestCase {
        String input;
        String output;

        public BaseSecIdTestCase(String input, String secondaryId) {
            this.input = input;
            this.output = secondaryId;
        }

        public String getInput() {
            return input;
        }

        public String getOutput() {
            return output;
        }
    }

    /**
     * Test method for {@link org.eclipse.tracecompass.tmf.ui.views.TmfViewFactory#getBaseSecId(java.lang.String)}.
     */
    @Test
    public void testGetBaseSecId() {
        for (BaseSecIdTestCase testCase : baseSecIdTestCases) {
            String input = testCase.input;
            String expect = testCase.output;
            String result = TmfViewFactory.getBaseSecId(input);
            String message = String.format("Input:%s Output: %s Expected: %s", input, result, expect);
            if (expect == null) {
                assertNull(message, result);
                continue;
            }
            assertTrue(message, result.equals(expect));
        }
    }

    /**
     * Test method for {@link org.eclipse.tracecompass.tmf.ui.views.TmfViewFactory#newView(java.lang.String, boolean)}.
     */
    @Test
    public void testNewView() {
        IViewPart firstView = TmfViewFactory.newView(TmfViewStub.TMF_VIEW_STUB_ID, false);
        IViewPart sameAsFirstView = TmfViewFactory.newView(TmfViewStub.TMF_VIEW_STUB_ID, false);
        IViewPart secondView = TmfViewFactory.newView(TmfViewStub.TMF_VIEW_STUB_ID, true);
        IViewPart failView1 = TmfViewFactory.newView("this.is.a.failing.view.id", false);
        IViewPart failView2 = TmfViewFactory.newView("this.is.a.failing.view.id", true);

        assertNotNull("Failed to spawn first view", firstView);
        assertEquals("Same id returned different instance", sameAsFirstView, firstView);
        assertNotNull("Failed to open second view with suffix", secondView);
        assertNull("Expected to fail on dummy view id", failView1);
        assertNull("Expected to fail on dummy view id with suffix", failView2);

        /** Test for new view from a duplicate view */
        /* Fetch duplicate view complete id */
        IWorkbench wb = PlatformUI.getWorkbench();
        IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
        IWorkbenchPage page = win.getActivePage();
        IViewReference[] viewRefs = page.getViewReferences();

        String fullId = null;
        for (IViewReference view : viewRefs) {
            if (view.getSecondaryId() != null && view.getId().equals(TmfViewStub.TMF_VIEW_STUB_ID)) {
                assertTrue("Instanceof a TMfViewStub", view.getView(false) instanceof TmfViewStub);
                fullId = ((TmfViewStub) view.getView(false)).getViewId();
                break;
            }
        }

        IViewPart thirdView = TmfViewFactory.newView(fullId, true);
        assertNotNull("Creation from a view id with suffix failed", fullId);
        assertFalse("New view from view id with suffix was not created", Arrays.asList(viewRefs).contains(thirdView));
    }
}
