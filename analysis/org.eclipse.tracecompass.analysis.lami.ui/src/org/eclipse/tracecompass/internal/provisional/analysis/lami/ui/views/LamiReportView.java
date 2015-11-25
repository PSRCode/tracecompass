/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.views;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;
import static org.eclipse.tracecompass.common.core.NonNullUtils.nullToEmptyString;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect.LamiEmptyAspect;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect.LamiTableEntryAspect;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel.ChartType;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiResultTable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types.LamiTimeRange;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.signals.LamiSelectionUpdateSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSelectionRangeUpdatedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfNanoTimestamp;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;

/**
 * Base view showing output of Babeltrace scripts.
 *
 * Implementations can specify which analysis modules to use, which will define
 * the scripts and parameters to use accordingly.
 *
 * @author Alexandre Montplaisir
 */
public final class LamiReportView extends TmfView {

    // ------------------------------------------------------------------------
    // Attributes
    // ------------------------------------------------------------------------

    /** View ID */
    public static final String VIEW_ID = "org.eclipse.tracecompass.analysis.lami.views.reportview"; //$NON-NLS-1$

    private final @Nullable LamiResultTable fResultTable;

    private @Nullable LamiViewerControl fTableViewerControl;
    private final Set<LamiViewerControl> fPredefGraphViewerControls = new LinkedHashSet<>();
    private final Set<LamiViewerControl> fCustomGraphViewerControls = new LinkedHashSet<>();
    private @Nullable SashForm fSashForm;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    /**
     * Constructor
     */
    public LamiReportView() {
        super(VIEW_ID);
        fResultTable = LamiReportViewFactory.getCurrentResultTable();

        /* Register to receive LamiSelectionUpdateSignal */
        TmfSignalManager.register(this);
    }

    // ------------------------------------------------------------------------
    // ViewPart
    // ------------------------------------------------------------------------

    @Override
    public void createPartControl(@Nullable Composite parent) {
        LamiResultTable resultTable = fResultTable;
        if (resultTable == null || parent == null) {
            return;
        }

        SashForm sf = new SashForm(parent, SWT.NONE);
        fSashForm = sf;
        setPartName(resultTable.getTableClass().getTableTitle());

        /* Prepare the table viewer, which is always present */
        LamiViewerControl tableViewerControl = new LamiViewerControl(sf, resultTable);
        fTableViewerControl = tableViewerControl;

        /* Prepare the predefined graph viewers, if any */
        resultTable.getTableClass().getPredefinedViews()
            .forEach(graphModel -> fPredefGraphViewerControls.add(new LamiViewerControl(sf, resultTable, graphModel)));

        /* Automatically open the table viewer initially */
        tableViewerControl.getToggleAction().run();

        /* Add toolbar buttons */
        IToolBarManager toolbarMgr = getViewSite().getActionBars().getToolBarManager();
        toolbarMgr.add(tableViewerControl.getToggleAction());
        fPredefGraphViewerControls.stream()
            .map(LamiViewerControl::getToggleAction)
            .forEach(toolbarMgr::add);

        IMenuManager menuMgr = getViewSite().getActionBars().getMenuManager();
        IAction newHistogramAction = new NewChartAction(checkNotNull(parent.getShell()), sf, resultTable, ChartType.HISTOGRAM);
        IAction newXYScatterAction = new NewChartAction(checkNotNull(parent.getShell()), sf, resultTable, ChartType.XY_SCATTER);

        newHistogramAction.setText(Messages.LamiReportView_NewCustomHistogram);
        newXYScatterAction.setText(Messages.LamiReportView_NewCustomScatterChart);


        IAction clearCustomViewsAction = new Action() {
            @Override
            public void run() {
                fCustomGraphViewerControls.forEach(LamiViewerControl::dispose);
                fCustomGraphViewerControls.clear();
                sf.layout();

            }
        };
        clearCustomViewsAction.setText(Messages.LamiReportView_ClearAllCustomViews);

        menuMgr.add(newHistogramAction);
        menuMgr.add(newXYScatterAction);
        menuMgr.add(new Separator());
        menuMgr.add(clearCustomViewsAction);
    }

    // ------------------------------------------------------------------------
    // Operations
    // ------------------------------------------------------------------------

    @Override
    public void setFocus() {
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fSashForm != null) {
            fSashForm.dispose();
        }
        if (fTableViewerControl != null) {
            fTableViewerControl.dispose();
        }
        fPredefGraphViewerControls.forEach(LamiViewerControl::dispose);
        fCustomGraphViewerControls.forEach(LamiViewerControl::dispose);
        TmfSignalManager.deregister(this);
    }

    private class NewChartAction extends Action {

        private final Shell icfDialogParentShell;
        private final Composite icfChartViewerParent;
        private final LamiResultTable icfResultTable;
        private boolean icfXLogScale;
        private boolean icfYLogScale;
        private final ChartType icfChartType;

        public NewChartAction(Shell parentShell, Composite chartViewerParent,
                LamiResultTable resultTable, ChartType chartType) {
            icfDialogParentShell = parentShell;
            icfChartViewerParent = chartViewerParent;
            icfResultTable = resultTable;
            icfXLogScale = false;
            icfYLogScale = false;
            icfChartType = chartType;
        }

        @Override
        public void run() {
            int xLogScaleOptionIndex = -1;
            int yLogScaleOptionIndex = -1;

            /* Basic filtering of column */
            List<@NonNull LamiTableEntryAspect> column = icfResultTable.getTableClass()
                    .getAspects().stream()
                    .filter(aspect -> !(aspect instanceof LamiEmptyAspect)).collect(Collectors.toList());

            /* Split into 2 collection for X and Y dialog */

            /* xColumn is used to represent either X-axis and categories*/
            Stream<@NonNull LamiTableEntryAspect> xColumn = column.stream();
            Stream<@NonNull LamiTableEntryAspect> yColumn  = column.stream();

            /* Restrict available column based on chart type */
            switch (icfChartType) {
            /* Scatter chart can only map numerical value as X axis and Y axis. TimeStamp are numerical */
            case XY_SCATTER:
                xColumn = xColumn.filter(aspect -> aspect.isNumerical());
                yColumn = yColumn.filter(aspect -> aspect.isNumerical());
                break;
            /* Histogram and pie charts accept either string or numerical value as X-axis and only numerical values make sense */
            case HISTOGRAM:
            case PIE_CHART:
                yColumn = yColumn.filter(aspect -> aspect.isNumerical() && !aspect.isTimeStamp());
                break;
            default:
                /* FIXME throw something */
                break;
            }

            /* Prepare dialogs input */
            List<String> xStringColumn = xColumn.map(aspect -> aspect.getName()).collect(Collectors.toList());
            List<String> yStringColumn = yColumn.map(aspect -> aspect.getName()).collect(Collectors.toList());


            /* X axis dialog */
            LamiAxisListDialog dialog = new LamiAxisListDialog(icfDialogParentShell);
            dialog.setContentProvider(ArrayContentProvider.getInstance());
            dialog.setLabelProvider(new LabelProvider() {
                @Override
                public String getText(@Nullable Object element) {
                    return (String) checkNotNull(element);
                }
            });
            dialog.setInput(xStringColumn);

            String dialogTitle = "New " + icfChartType.toString().toLowerCase() + " graph ";  //$NON-NLS-1$//$NON-NLS-2$

            dialog.setTitle(dialogTitle);


            /* Chart specific action for X Axis */
            switch (icfChartType) {
            case XY_SCATTER:
                /* Enable log scale option for X axis */
                xLogScaleOptionIndex = dialog.addCheckBoxOption(nullToEmptyString(Messages.LamiReportView_LogScale), false);
                dialog.setMessage(Messages.LamiReportView_SelectColumnForX);
                break;
            case HISTOGRAM:
                dialog.setMessage(Messages.LamiReportView_SelectColumnsForCategories);
                break;
            case PIE_CHART:
            default:
                break;
            }

            if (dialog.open() != Window.OK) {
                return;
            }
            Object[] results = dialog.getResult();
            boolean[] checkBoxOptionsResults = dialog.getCheckBoxOptionValues();

            String xAxisCol = Arrays.stream(results)
                .map(elem -> { return (String) elem;} )
                .findFirst()
                .get();

            /* Get log scale X axis option */
            if (xLogScaleOptionIndex > -1 && xLogScaleOptionIndex < checkBoxOptionsResults.length) {
                icfXLogScale = checkBoxOptionsResults[xLogScaleOptionIndex];
            }

            /* Y axis dialog */
            LamiAxisListSelectionDialog dialog2 = new LamiAxisListSelectionDialog(icfDialogParentShell,
                    checkNotNull(yStringColumn),
                    checkNotNull(ArrayContentProvider.getInstance()),
                    new LabelProvider() {
                        @Override
                        public String getText(@Nullable Object element) {
                            return (String) checkNotNull(element);
                        }
                    },
                    nullToEmptyString(Messages.LamiReportView_SelectColumnsForSeries));

            dialog2.setTitle(dialogTitle);


            /* Add options for Y axis by chart type */
            switch (icfChartType) {
            case HISTOGRAM:
            case XY_SCATTER:
                yLogScaleOptionIndex = dialog2.addCheckBoxOption(nullToEmptyString(Messages.LamiReportView_LogScale), false);
                break;
            case PIE_CHART:
            default:
                break;
            }

            if (dialog2.open() != Window.OK) {
                return;
            }

            results = dialog2.getResult();
            checkBoxOptionsResults = dialog2.getCheckBoxOptionValues();

            List<String> seriesCols = Arrays.stream(results)
                .map(elem -> { return (String) elem;} )
                .collect(Collectors.toList());

            /* Get Y log scale option */
            if (yLogScaleOptionIndex > -1 && yLogScaleOptionIndex < checkBoxOptionsResults.length ) {
                icfYLogScale = checkBoxOptionsResults[yLogScaleOptionIndex];
            }

            LamiChartModel graphModel = new LamiChartModel(icfChartType, nullToEmptyString(Messages.LamiReportView_Custom),
                    xAxisCol, checkNotNull(seriesCols), icfXLogScale, icfYLogScale);
            LamiViewerControl viewerControl = new LamiViewerControl(icfChartViewerParent, icfResultTable, graphModel);
            fCustomGraphViewerControls.add(viewerControl);
            viewerControl.getToggleAction().run();
        }
    }

    // ------------------------------------------------------------------------
    // Signals
    // ------------------------------------------------------------------------

    /**
     * Signal handler for selection update.
     * Propagate a TmfSelectionRangeUpdatedSignal if possible.
     *
     * @param signal
     *          The selection update signal
     */
    @TmfSignalHandler
    public void updateSelection(LamiSelectionUpdateSignal signal) {
        LamiResultTable table = fResultTable;
        if (table == null) {
            return;
        }

        if (table.hashCode() != signal.getSignalHash() || signal.getSource() == this) {
            /* The signal is not for us */
            return;
        }

        LamiTableEntry selectedEntry = table.getEntries().get(signal.getEntryIndex());
        LamiTimeRange range = selectedEntry.getCorrespondingTimeRange();

        if (range == null) {
            /* This entry does not supply range information. */
            return;
        }

        /* Send Range update to other views */
        ITmfTimestamp start = new TmfNanoTimestamp(range.getStart());
        ITmfTimestamp end = new TmfNanoTimestamp(range.getEnd());
        TmfSignalManager.dispatchSignal(new TmfSelectionRangeUpdatedSignal(LamiReportView.this, start, end));
    }

    /**
     * Signal handler for time range selections
     *
     * @param signal
     *            The received signal
     */
    @TmfSignalHandler
    public void externalUpdateSelection(TmfSelectionRangeUpdatedSignal signal) {
        LamiResultTable table = fResultTable;
        if (table == null) {
            return;
        }

        if (signal.getSource() == this) {
            /* We are the source */
            return;
        }
        TmfTimeRange range = new TmfTimeRange(signal.getBeginTime(), signal.getEndTime());

        for (LamiTableEntry entry : table.getEntries()) {
            LamiTimeRange timerange = entry.getCorrespondingTimeRange();
            if (timerange == null) {
                /* Return since the table have no timerange */
                return;
            }

            TmfTimeRange tempTimeRange = new TmfTimeRange(new TmfNanoTimestamp(timerange.getStart()), new TmfNanoTimestamp(timerange.getEnd()));
            if (tempTimeRange.getIntersection(range) != null) {
                /* It intersect */
                /*
                 * For now only select the first entry that intersect with the
                 * timerange
                 */
                int firstIndex = table.getEntries().indexOf(entry);
                /* Update all LamiViewer */
                LamiSelectionUpdateSignal signal1 = new LamiSelectionUpdateSignal(LamiReportView.this, firstIndex, table.hashCode());
                TmfSignalManager.dispatchSignal(signal1);
                return;
            }
        }
    }
}
