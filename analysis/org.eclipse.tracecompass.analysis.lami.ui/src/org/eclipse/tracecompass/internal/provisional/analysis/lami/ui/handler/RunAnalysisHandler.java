/*******************************************************************************
 * Copyright (c) 2015, 2016 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.handler;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;
import static org.eclipse.tracecompass.common.core.NonNullUtils.nullToEmptyString;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiAnalysis;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiAnalysisReport;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiResultTable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.views.LamiReportViewFactory;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.IOndemandAnalysis;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.IOndemandAnalysisReport;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.OndemandAnalysisException;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfCommonProjectElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfOndemandAnalysisElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfReportsElement;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * The command handler for the "Run External Analysis" menu option.
 *
 * @author Alexandre Montplaisir
 */
public class RunAnalysisHandler extends AbstractHandler {

    private @Nullable TmfOndemandAnalysisElement fAnalysisElem;

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
         * Make sure there is only one selection and that it is a on-demand
         * analysis element.
         */
        fAnalysisElem = null;
        if (selection instanceof TreeSelection) {
            final TreeSelection sel = (TreeSelection) selection;
            // There should be only one item selected as per the plugin.xml
            final Object element = sel.getFirstElement();
            if (element instanceof TmfOndemandAnalysisElement) {
                TmfOndemandAnalysisElement elem = (TmfOndemandAnalysisElement) element;
                if (elem.getAnalysis() instanceof LamiAnalysis && elem.canRun()) {
                    fAnalysisElem = elem;
                }
            }
        }

        return (fAnalysisElem != null);
    }

    @Override
    public @Nullable Object execute(@Nullable ExecutionEvent event) throws ExecutionException {
        // Check if we are closing down
        final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            return null;
        }

        // Check that the trace is valid
        TmfOndemandAnalysisElement analysisElem = fAnalysisElem;
        if (analysisElem == null) {
            return null;
        }

        TmfCommonProjectElement traceElem = analysisElem.getParent().getParent();
        ITmfTrace trace = traceElem.getTrace();
        if (trace == null) {
            return null;
        }

        /* Retrieve and initialize the analysis module, aka read the script's metadata */
        IOndemandAnalysis ondemandAnalysis = analysisElem.getAnalysis();
        if (!(ondemandAnalysis instanceof LamiAnalysis)) {
            return null;
        }
        LamiAnalysis analysis = (LamiAnalysis) ondemandAnalysis;

        /* Retrieve the current time range, will be used as parameters to the analysis */
        TmfTraceManager tm = TmfTraceManager.getInstance();
        TmfTimeRange timeRange = tm.getCurrentTraceContext().getSelectionRange();
        if (timeRange.getStartTime().equals(timeRange.getEndTime())) {
            timeRange = null;
        }
        /* Job below needs a final reference... */
        final TmfTimeRange tr = timeRange;

        /* Pop the dialog to ask for extra parameters */
        String baseCommand = analysis.getBaseCommandAsString(trace, tr);

        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        ParameterDialog dialog = new ParameterDialog(shell, Messages.ParameterDialog_ExternalParameters,
                Messages.ParameterDialog_ExternalParametersDescription,
                        baseCommand,
                PARAM_STRING_VALIDATOR);
        if (dialog.open() != Window.OK) {
            /* User clicked Cancel, don't run */
            return null;
        }
        String extraParams = nullToEmptyString(dialog.getValue());

        /* Execute the analysis and produce the reports */
        Job job = new Job(Messages.LamiAnalysis_MainTaskName) {
            @Override
            protected @Nullable IStatus run(@Nullable IProgressMonitor monitor) {
                IProgressMonitor mon = (monitor == null ? new NullProgressMonitor() : monitor);
                try {
                    List<LamiResultTable> results = analysis.execute(trace, tr, extraParams, mon);

                    String reportName = analysis.getName() +' ' + Messages.ParameterDialog_ReportNameSuffix;
                    LamiAnalysisReport report = new LamiAnalysisReport(reportName, results);
                    registerNewReport(report);

                    /* Automatically open the report for convenience */
                    Display.getDefault().syncExec(() -> {
                        try {
                            LamiReportViewFactory.createNewViews(report);
                        } catch (PartInitException e) {
                        }
                    });
                    return Status.OK_STATUS;

                } catch (OndemandAnalysisException e) {
                    String errMsg = e.getMessage();

                    if (errMsg != null) {
                        /* The analysis execution yielded an error */
                        Display.getDefault().asyncExec(() -> {
                            MessageDialog.openError(shell,
                                    /* Dialog title */
                                    Messages.ParameterDialog_Error,
                                    /* Dialog message */
                                    Messages.ParameterDialog_ErrorMessage + ":\n\n" + //$NON-NLS-1$
                                            errMsg);
                        });
                    }

                    return Status.CANCEL_STATUS;
                }
            }
        };
        job.schedule();

        return null;
    }

    private static final IInputValidator PARAM_STRING_VALIDATOR = text -> {
        if (text.isEmpty() || text.matches("[a-zA-Z0-9\\,\\-\\s]+")) { //$NON-NLS-1$
            return null;
        }
        return Messages.ParameterDialog_StringValidatorMessage;
    };

    /**
     * Register a new report
     *
     * @param report
     *            The report to add
     */
    public void registerNewReport(IOndemandAnalysisReport report) {
        /* For now the TmfProjectReportsElement manages the reports. */
        TmfReportsElement reportsElement = checkNotNull(fAnalysisElem)
                .getParent()
                .getParent()
                .getReportsChildElement();

        reportsElement.addReport(report);
    }

}
