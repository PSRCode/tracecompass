/*******************************************************************************
 * Copyright (c) 2016 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.handler;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.IOndemandAnalysisReport;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfReportElement;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * The command handler for the "Delete Report" menu option for Report project
 * model elements.
 *
 * @author Alexandre Montplaisir
 */
public class DeleteReportHandler extends AbstractHandler {

    /** Report elements to delete */
    private @Nullable List<TmfReportElement> fReportElements;

    @Override
    public boolean isEnabled() {
        // Check if we are closing down
        final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return false;
        }

        // Get the selection
        final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        final IWorkbenchPart part = page.getActivePart();
        if (part == null) {
            return false;
        }
        final ISelectionProvider selectionProvider = part.getSite().getSelectionProvider();
        if (selectionProvider == null) {
            return false;
        }
        final ISelection selection = selectionProvider.getSelection();

        /*
         * Get the corresponding project elements from the selection.
         */
        fReportElements = null;
        if (selection instanceof TreeSelection) {
            final TreeSelection sel = (TreeSelection) selection;
            /*
             * There can be more than one element selected, but they should all
             * be report elements, as per the plugin.xml.
             */
            List<?> elements = sel.toList();
            List<TmfReportElement> reportElements = elements.stream()
                .filter(elem -> elem instanceof TmfReportElement)
                .map(elem -> (TmfReportElement) elem)
                .collect(Collectors.toList());
            fReportElements = reportElements;
        }

        return (fReportElements != null);
    }

    @Override
    public @Nullable Object execute(@Nullable ExecutionEvent event) throws ExecutionException {
        // Check if we are closing down
        final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return null;
        }

        // Check that the elements are valid
        List<TmfReportElement> reportElems = fReportElements;
        if (reportElems == null) {
            return null;
        }

        for (TmfReportElement reportElem : reportElems) {
            IOndemandAnalysisReport report = reportElem.getReport();
            reportElem.getParent().removeReport(report);
        }

        return null;
    }
}
