/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.ctf.ui.streamintersection;

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
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.ctf.core.trace.CtfTmfTrace;
import org.eclipse.tracecompass.tmf.ui.project.handlers.HandlerUtils;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfOpenTraceHelper;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfProjectElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceElement;
import org.eclipse.tracecompass.tmf.ui.project.model.TmfTraceFolder;
import org.eclipse.tracecompass.tmf.ui.project.model.TraceUtils;
import org.eclipse.tracecompass.tmf.ui.project.operations.TmfWorkspaceModifyOperation;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler for the "Stream Intersection" action for CTF traces.
 *
 * @author Alexandre Montplaisir
 */
public class StreamIntersectionHandler extends AbstractHandler {

    /**
     * Suffix for new traces with stream intersection, added to the original
     * trace name.
     */
    private static final String TRACE_NAME_SUFFIX = "-stream-intersect"; //$NON-NLS-1$

    @Override
    public boolean isEnabled() {
        final Object element = HandlerUtils.getSelectedModelElement();
        if (element == null) {
            return false;
        }

        /*
         * Only available for CTF (ctf.tmf) traces.
         *
         * plugin.xml should have done element type/count verification already.
         */
        TmfTraceElement traceElem = (TmfTraceElement) element;
        return (traceElem.getTrace() instanceof CtfTmfTrace);
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
        /* plugin.xml should have already verified the trace type */
        CtfTmfTrace ctfTmfTrace = (CtfTmfTrace) trace;

        /*
         * Pop a dialog asking the user to select a parent directory for the new
         * trace.
         */
        final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        DirectoryDialog dialog = new DirectoryDialog(shell);
        dialog.setText(Messages.StreamIntersection_DirectoryChooser_DialogTitle);
        String result = dialog.open();
        if (result == null) {
            /* Dialog was cancelled, take no further action. */
            return null;
        }

        /* Verify that the selected path is valid and writeable */
        final Path parentPath = checkNotNull(Paths.get(result));
        if (!Files.isDirectory(parentPath)) {
            MessageDialog.openError(shell, Messages.StreamIntersection_InvalidDirectory_DialogTitle, Messages.StreamIntersection_InvalidDirectory_DialogText);
            return null;
        }
        if (!Files.isWritable(parentPath)) {
            MessageDialog.openError(shell, Messages.StreamIntersection_InvalidDirectory_DialogTitle, Messages.StreamIntersection_NoWriteAccess_DialogText);
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

        TmfWorkspaceModifyOperation streamIntersectionOperation = new TmfWorkspaceModifyOperation() {
            @Override
            public void execute(@Nullable IProgressMonitor monitor) throws CoreException {
                IProgressMonitor mon = (monitor == null ? new NullProgressMonitor() : monitor);

                ctfTmfTrace.streamIntersection(tracePath, mon);

                /* Import the new trace into the current project. */
                TmfProjectElement currentProjectElement = traceElem.getProject();
                TmfTraceFolder traceFolder = currentProjectElement.getTracesFolder();
                TmfOpenTraceHelper.openTraceFromPath(traceFolder, tracePath.toString(), shell);
            }
        };

        try {
            PlatformUI.getWorkbench().getProgressService().run(true, true, streamIntersectionOperation);
        } catch (InterruptedException e) {
            return null;
        } catch (InvocationTargetException e) {
            TraceUtils.displayErrorMsg(e.toString(), e.getTargetException().toString());
            return null;
        }

        return null;
    }

}
