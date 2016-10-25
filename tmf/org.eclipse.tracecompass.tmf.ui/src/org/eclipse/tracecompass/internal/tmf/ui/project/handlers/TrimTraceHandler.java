/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.ui.project.handlers;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.internal.tmf.ui.project.operations.TmfWorkspaceModifyOperation;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.trim.ITmfTrimmableTrace;
import org.eclipse.tracecompass.tmf.ui.project.handlers.HandlerUtils;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfOpenTraceHelper;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;
import org.eclipse.tracecompass.tmf.ui.project.model.TraceUtils;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler for the Trace Trim operation.
 *
 * @author Alexandre Montplaisir
 */
@NonNullByDefault
public class TrimTraceHandler extends AbstractHandler {

    /** Suffix for new trimmed traces, added to the original trace name */
    private static final String TRACE_NAME_SUFFIX = "-trimmed"; //$NON-NLS-1$

    @Override
    public boolean isEnabled() {
        final Object element = HandlerUtils.getSelectedModelElement();
        if (element == null) {
            return false;
        }

        /*
         * plugin.xml should have done type/count verification already
         */
        TmfTraceElement traceElem = (TmfTraceElement) element;
        if (!(traceElem.getTrace() instanceof ITmfTrimmableTrace)) {
            return false;
        }

        /* Only enable the action if a time range is currently selected */
        TmfTraceManager tm = TmfTraceManager.getInstance();
        TmfTimeRange selectionRange = tm.getCurrentTraceContext().getSelectionRange();
        if (selectionRange.getStartTime().equals(selectionRange.getEndTime())) {
            return false;
        }

        return true;
    }

    @Override
    public @Nullable Object execute(@Nullable ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
        Object element = ((IStructuredSelection) selection).getFirstElement();
        final TmfTraceElement traceElem = (TmfTraceElement) element;

        ITmfTrace trace = traceElem.getTrace();
        if (trace == null) {
            /* That trace is not currently opened */
            return null;
        }
        ITmfTrimmableTrace trimmableTrace = (ITmfTrimmableTrace) trace;


        /* Retrieve the current time range */
        final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        TmfTraceManager tm = TmfTraceManager.getInstance();
        TmfTimeRange timeRange = tm.getCurrentTraceContext().getSelectionRange();
        if (timeRange.getStartTime().equals(timeRange.getEndTime())) {
            MessageDialog.openError(shell, Messages.TrimTraces_InvalidTimeRange_DialogTitle, Messages.TrimTraces_InvalidTimeRange_DialogText);
            return null;
        }

        /* Ensure the time range is in the right direction */
        final TmfTimeRange tr = ((timeRange.getStartTime().compareTo(timeRange.getEndTime()) > 0) ?
                new TmfTimeRange(timeRange.getEndTime(), timeRange.getStartTime()) :
                    timeRange);

        /*
         * Pop a dialog asking the user to select a parent directory for the new
         * trace.
         */
        DirectoryDialog dialog = new DirectoryDialog(shell);
        dialog.setText(Messages.TrimTraces_DirectoryChooser_DialogTitle);
        String result = dialog.open();
        if (result == null) {
            /* Dialog was cancelled, take no further action. */
            return null;
        }

        /* Verify that the selected path is valid and writeable */
        final Path parentPath = checkNotNull(Paths.get(result));
        if (!Files.isDirectory(parentPath)) {
            MessageDialog.openError(shell, Messages.TrimTraces_InvalidDirectory_DialogTitle, Messages.TrimTraces_InvalidDirectory_DialogText);
            return null;
        }
        if (!Files.isWritable(parentPath)) {
            MessageDialog.openError(shell, Messages.TrimTraces_InvalidDirectory_DialogTitle, Messages.TrimTraces_NoWriteAccess_DialogText);
            return null;
        }

        /*
         * Create a directory for the new trace. We will pick the next available
         * name, adding -2, -3, etc. as needed.
         */
        String newTraceName = trace.getName() + TRACE_NAME_SUFFIX;
        Path potentialPath = parentPath.resolve(newTraceName);
        for (int i = 2; Files.exists(potentialPath); i++) {
            newTraceName = trace.getName() + TRACE_NAME_SUFFIX + '-' + String.valueOf(i);
            potentialPath = parentPath.resolve(newTraceName);
        }

        final Path tracePath = checkNotNull(potentialPath);
        try {
            Files.createDirectory(tracePath);
        } catch (IOException e) {
            /* Should not happen since we have checked permissions, etc. */
            throw new IllegalStateException(e);
        }

        TmfWorkspaceModifyOperation trimOperation = new TmfWorkspaceModifyOperation() {
            @Override
            public void execute(@Nullable IProgressMonitor monitor) throws CoreException {
                IProgressMonitor mon = (monitor == null ? new NullProgressMonitor() : monitor);

                /* Perform the trace-specific trim operation. */
                trimmableTrace.trim(tr, tracePath, mon);

                /* Import the new trace into the current project, at the top-level. */
                TmfProjectElement currentProjectElement = traceElem.getProject();
                TmfTraceFolder traceFolder =currentProjectElement.getTracesFolder();
                TmfOpenTraceHelper.openTraceFromPath(traceFolder, tracePath.toString(), shell);
            }
        };

        try {
            PlatformUI.getWorkbench().getProgressService().run(true, true, trimOperation);
        } catch (InterruptedException e) {
            return null;
        } catch (InvocationTargetException e) {
            TraceUtils.displayErrorMsg(e.toString(), e.getTargetException().toString());
            return null;
        }

        return null;
    }

}
