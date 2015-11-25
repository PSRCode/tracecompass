/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Michael Jeanson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.viewers;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;
import static org.eclipse.tracecompass.common.core.NonNullUtils.nullToEmptyString;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect.LamiTableEntryAspect;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiResultTable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.signals.LamiSelectionUpdateSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.ui.viewers.TmfViewer;
import org.swtchart.Chart;
import org.swtchart.ITitle;

import com.google.common.collect.ImmutableList;

/**
 * Abstract XYChart Viewer for LAMI views.
 *
 * @author Michael Jeanson
 *
 */
public abstract class LamiXYChartViewer extends TmfViewer implements ILamiViewer {

    /** Ellipsis character */
    protected static final String ELLIPSIS = "…"; //$NON-NLS-1$

    /**
     * String representing unknown values. Can be present even in numerical
     * aspects!
     */
    protected static final String UNKNOWN = "?"; //$NON-NLS-1$

    /**
     * Function to use to map Strings read from the data table to doubles for
     * use in SWTChart series.
     */
    protected static final ToDoubleFunction<@Nullable String> DOUBLE_MAPPER = str -> {
        if (str == null || str.equals(UNKNOWN)) {
            return 0.0;
        }
        return Double.parseDouble(str);
    };

    /**
     * List of standard colors
     */
    protected static final List<@NonNull Color> COLORS = ImmutableList.of(
                new Color(Display.getDefault(),  72, 120, 207),
                new Color(Display.getDefault(), 106, 204, 101),
                new Color(Display.getDefault(), 214,  95,  95),
                new Color(Display.getDefault(), 180, 124, 199),
                new Color(Display.getDefault(), 196, 173, 102),
                new Color(Display.getDefault(), 119, 190, 219)
                );

    /**
     * List of "light" colors (when unselected)
     */
    protected static final List<@NonNull Color> LIGHT_COLORS = ImmutableList.of(
                new Color(Display.getDefault(), 173, 195, 233),
                new Color(Display.getDefault(), 199, 236, 197),
                new Color(Display.getDefault(), 240, 196, 196),
                new Color(Display.getDefault(), 231, 213, 237),
                new Color(Display.getDefault(), 231, 222, 194),
                new Color(Display.getDefault(), 220, 238, 246)
                );

    private final Listener fResizeListener = event -> {
        /* Refresh the titles to fit the current chart size */
        refreshDisplayTitles();

        /* Refresh the Axis labels to fit the current chart size */
        refreshDisplayLabels();
    };

    private final LamiResultTable fResultTable;
    private final LamiChartModel fChartModel;

    private final Chart fChart;

    private final String fChartTitle;
    private final String fXTitle;
    private final String fYTitle;

    private boolean fSelected;
    private int fSelection;

    /**
     * Creates a Viewer instance based on SWTChart.
     *
     * @param parent
     *            The parent composite to draw in.
     * @param resultTable
     *            The result table containing the data from which to build the
     *            chart
     * @param chartModel
     *            The information about the chart to build
     */
    public LamiXYChartViewer(Composite parent, LamiResultTable resultTable, LamiChartModel chartModel) {
        super(parent);

        fParent = parent;
        fResultTable = resultTable;
        fChartModel = chartModel;

        fChart = new Chart(parent, SWT.NONE);
        fChart.addListener(SWT.Resize, fResizeListener);

        unsetSelection();

        /* Set Chart title */
        fChartTitle = fResultTable.getTableClass().getTableTitle();

        /* Set X axis title */
        fXTitle = getChartModel().getXAxisColumn();

        /* Set Y axis title */
        if (fChartModel.getSeriesColumns().size() == 1) {
            /*
             * There is only 1 series in the chart, we will use its name as the
             * Y axis (and hide the legend).
             */
            String seriesName = getChartModel().getSeriesColumns().get(0);
            fYTitle = seriesName;
            fChart.getLegend().setVisible(false);
        } else {
            /*
             * There are multiple series in the chart, if they all share the same
             * units, display that.
             */
            long nbDiffAspects = getYAxisAspects().stream()
                .map(aspect -> aspect.getUnits())
                .distinct()
                .count();

            if (nbDiffAspects == 1) {
                /* All aspects use the same unit type */
                String units = getYAxisAspects().get(0).getUnits();
                fYTitle = Messages.LamiViewer_DefaultValueName + " (" + units + ')'; //$NON-NLS-1$
            } else {
                /* Various unit types, just say "Value" */
                fYTitle = nullToEmptyString(Messages.LamiViewer_DefaultValueName);
            }
        }

        /* Set all titles and labels font color to black */
        fChart.getTitle().setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
        fChart.getAxisSet().getXAxis(0).getTitle().setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
        fChart.getAxisSet().getYAxis(0).getTitle().setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
        fChart.getAxisSet().getXAxis(0).getTick().setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
        fChart.getAxisSet().getYAxis(0).getTick().setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));

        /* Set X label 90 degrees */
        fChart.getAxisSet().getXAxis(0).getTick().setTickLabelAngle(90);

        /* TODO: Set X label format depending on datatype? */
        //fChart.getAxisSet().getXAxis(0).setFormat(new DecimalFormat("#####.#M"));

        /* Refresh the titles to fit the current chart size */
        refreshDisplayTitles();
    }

    /**
     * Get the chart result table.
     *
     * @return The chart result table.
     */
    protected LamiResultTable getResultTable() {
        return fResultTable;
    }

    /**
     * Get the chart model.
     *
     * @return The chart model.
     */
    protected LamiChartModel getChartModel() {
        return fChartModel;
    }

    /**
     * Get the chart object.
     * @return The chart object.
     */
    protected Chart getChart() {
        return fChart;
    }

    /**
     * Is a selection made in the chart.
     *
     * @return true if there is a selection.
     */
    protected boolean isSelected() {
        return fSelected;
    }

    /**
     * Set the selection index.
     *
     * @param selection the index to select.
     */
    protected void setSelection(int selection) {
        fSelection = selection;
        fSelected = true;
    }

    /**
     * Unset the chart selection.
     */
    protected void unsetSelection() {
        fSelection = 0;
        fSelected = false;
    }

    /**
     * Get the current selection index.
     *
     * @return the current selection index.
     */
    protected int getSelection() {
        return fSelection;
    }

    @Override
    public @Nullable Control getControl() {
        return fChart.getParent();
    }

    @Override
    public void refresh() {
        fChart.redraw();
    }

    @Override
    public void dispose() {
        fChart.dispose();
        super.dispose();
    }

    /**
     * Get a list of all the aspect of the Y axis.
     *
     * @return The aspets for the Y axis
     */
    protected List<LamiTableEntryAspect> getYAxisAspects() {

        List<LamiTableEntryAspect> yAxisAspects = new ArrayList<>();

        for (String colName : getChartModel().getSeriesColumns()) {
            yAxisAspects.add(getAspectFromName(getResultTable().getTableClass().getAspects(), colName));
        }

        return yAxisAspects;
    }

    /**
     * Set the ITitle object text to a substring of canonicalTitle that when
     * rendered in the chart will fit maxPixelLength.
     */
    private void refreshDisplayTitle(ITitle title, String canonicalTitle, int maxPixelLength) {
        if (title.isVisible()) {

            String newTitle = canonicalTitle;

            /* Get the title font */
            Font font = title.getFont();

            GC gc = new GC(fParent);
            gc.setFont(font);

            /* Get the length and height of the canonical title in pixels */
            Point pixels = gc.stringExtent(canonicalTitle);

            /*
             * If the title is too long, generate a shortened version based on the
             * average character width of the current font.
             */
            if (pixels.x > maxPixelLength) {
                int charwidth = gc.getFontMetrics().getAverageCharWidth();

                int minimum = 3;

                int strLen = ((maxPixelLength / charwidth) - minimum);

                if (strLen > minimum) {
                    newTitle = canonicalTitle.substring(0, strLen) + ELLIPSIS;
                } else {
                    newTitle = ELLIPSIS;
                }
            }

            title.setText(newTitle);

            // Cleanup
            gc.dispose();
        }
    }

    /**
     * Refresh the Chart, XAxis and YAxis titles to fit the current
     * chart size.
     */
    private void refreshDisplayTitles() {
        Rectangle chartRect = fChart.getClientArea();
        Rectangle plotRect = fChart.getPlotArea().getClientArea();

        ITitle chartTitle = checkNotNull(fChart.getTitle());
        refreshDisplayTitle(chartTitle, fChartTitle, chartRect.width);

        ITitle xTitle = checkNotNull(fChart.getAxisSet().getXAxis(0).getTitle());
        refreshDisplayTitle(xTitle, fXTitle, plotRect.width);

        ITitle yTitle = checkNotNull(fChart.getAxisSet().getYAxis(0).getTitle());
        refreshDisplayTitle(yTitle, fYTitle, plotRect.height);
    }

    /**
     * Get the aspect with th given name
     *
     * @param aspects
     *            The list of aspects to search into
     * @param aspectName
     *            The name of the aspect we are looking for
     * @return The corresponding aspect
     */
    protected static LamiTableEntryAspect getAspectFromName(List<LamiTableEntryAspect> aspects, String aspectName) {
        return aspects.stream()
                .filter(aspect -> (aspect.getName().equals(aspectName)))
                .findFirst()
                .get();
    }

    /**
     * Refresh the axis labels to fit the current chart size.
     */
    protected abstract void refreshDisplayLabels();

    /**
     * Redraw the chart.
     */
    protected void redraw() {
        getChart().redraw();
    }

    /**
     * Signal handler for selection update.
     *
     * @param signal
     *          The selection update signal
     */
    @TmfSignalHandler
    public void updateSelection(LamiSelectionUpdateSignal signal) {
        if (getResultTable().hashCode() != signal.getSignalHash() || signal.getSource() == this) {
            /* The signal is not for us */
            return;
        }

        /* Update the rectangle to be custom painted for selection visual reference */
        setSelection(signal.getEntryIndex());

        redraw();
    }
}
