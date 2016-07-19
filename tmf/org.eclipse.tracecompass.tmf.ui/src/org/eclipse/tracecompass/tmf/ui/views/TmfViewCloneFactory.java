/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Jonathan Rajotte-Julien
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.tmf.ui.views;


import java.util.UUID;

import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Factory to instantiate and display new TmfViews clone.
 *
 * @author Jonathan Rajotte-Julien
 * @since 3.0
 */
public class TmfViewCloneFactory {
    /**
     * Create all the views from a given report
     *
     * @param primaryId
     *            The primary view id
     * @throws PartInitException
     *             If there was a problem initializing a view
     */
    public static synchronized void createNewView(String primaryId)
            throws PartInitException {

        final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        page.showView(primaryId, String.valueOf(UUID.randomUUID()), IWorkbenchPage.VIEW_ACTIVATE);
    }

}
