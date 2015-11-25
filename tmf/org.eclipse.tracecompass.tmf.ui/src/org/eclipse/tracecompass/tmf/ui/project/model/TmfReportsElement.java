/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.tmf.ui.project.model;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.graphics.Image;
import org.eclipse.tracecompass.tmf.core.analysis.ondemand.IOndemandAnalysisReport;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Project model element for the "On-demand" / "External Analyses" element,
 * which goes under individual trace and experiment elements.
 *
 * @author Alexandre Montplaisir
 * @since 2.0
 */
public class TmfReportsElement extends TmfProjectModelElement {

    /**
     * Element of the resource path
     */
    public static final String PATH_ELEMENT = "reports"; //$NON-NLS-1$

    private static final String ELEMENT_NAME = Messages.TmfReportsElement_Name;

    private static final Pattern DIGITS_AT_END_PATTERN = Pattern.compile("(\\d+$)"); //$NON-NLS-1$

    private final BiMap<String, IOndemandAnalysisReport> fCurrentReports = HashBiMap.create();

    /**
     * Constructor
     *
     * @param resource
     *            The resource to be associated with this element
     * @param parent
     *            The parent element
     */
    protected TmfReportsElement(IResource resource, TmfCommonProjectElement parent) {
        super(ELEMENT_NAME, resource, parent);
    }

    @Override
    public TmfCommonProjectElement getParent() {
        /* Type enforced at constructor */
        return (TmfCommonProjectElement) super.getParent();
    }

    @Override
    public Image getIcon() {
        return TmfProjectModelIcons.REPORTS_ICON;
    }

    @Override
    protected void refreshChildren() {
        /* No children at the moment */
    }

    /**
     * Add a new report under this element.
     *
     * @param report
     *            The report to add
     */
    public void addReport(IOndemandAnalysisReport report) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IPath nodePath = this.getResource().getFullPath();

        /* Append #1,2,3 to the name if needed */
        String reportDisplayName = report.getName();
        if (fCurrentReports.containsKey(reportDisplayName)) {
            reportDisplayName = reportDisplayName + " #2"; //$NON-NLS-1$
            for (int nb = 3; fCurrentReports.containsKey(reportDisplayName); nb++) {
                Matcher matcher = DIGITS_AT_END_PATTERN.matcher(reportDisplayName);
                reportDisplayName = matcher.replaceFirst(String.valueOf(nb));
            }
        }

        fCurrentReports.put(reportDisplayName, report);

        IFolder analysisRes = checkNotNull(root.getFolder(nodePath.append(reportDisplayName)));
        TmfReportElement elem = new TmfReportElement(reportDisplayName, analysisRes, this, report);
        addChild(elem);
        refresh();
    }

    /**
     * Remove a report from under this element.
     *
     * @param report
     *            The report to remove
     */
    public void removeReport(IOndemandAnalysisReport report) {
        String displayName = fCurrentReports.inverse().get(report);
        fCurrentReports.values().remove(report);

        ITmfProjectModelElement elementToRemove = getChildren().stream()
                .filter(elem -> elem.getName().equals(displayName))
                .findFirst().orElse(null);
        removeChild(elementToRemove);
        refresh();
    }
}
