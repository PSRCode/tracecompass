/**********************************************************************
 * Copyright (c) 2016 EfficiOS Inc.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/
package org.eclipse.tracecompass.tmf.ui.tests.stubs.views;

import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * Simple TmfView stub
 * @author Jonathan Rajotte Julien
 */
public class TmfViewStub extends TmfView {

    @SuppressWarnings("javadoc")
    public static String TMF_VIEW_STUB_ID = "org.eclipse.tracecompass.tmf.ui.tests.stubs.views.TmfViewStub";

    @SuppressWarnings("javadoc")
    public TmfViewStub() {
        super(TMF_VIEW_STUB_ID);
    }
    @Override
    public void setFocus() {
    }

    @Override
    public String getViewId() {
        return super.getViewId();
    }

}
