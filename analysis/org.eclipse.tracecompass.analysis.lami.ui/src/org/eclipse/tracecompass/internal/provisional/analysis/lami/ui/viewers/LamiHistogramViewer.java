/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.viewers;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.tracecompass.common.core.format.DecimalUnitFormat;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect.LamiTableEntryAspect;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel.ChartType;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiResultTable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.signals.LamiSelectionUpdateSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.swtchart.IAxis;
import org.swtchart.IBarSeries;
import org.swtchart.ISeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.Range;

import com.google.common.collect.Iterators;

/**
 * Histogram Viewer for LAMI views.
 *
 * @author Alexandre Montplaisir
 */
public class LamiHistogramViewer extends LamiXYChartViewer {

    private final String[] fCategories;

    /**
     * Creates a Histogram Viewer instance based on SWTChart.
     *
     * @param parent
     *            The parent composite to draw in.
     * @param resultTable
     *            The result table containing the data from which to build the
     *            chart
     * @param chartModel
     *            The information about the chart to build
     */
    public LamiHistogramViewer(Composite parent, LamiResultTable resultTable, LamiChartModel chartModel) {
        super(parent, resultTable, chartModel);

        if (getChartModel().getChartType() != ChartType.HISTOGRAM) {
            throw new IllegalStateException();
        }

        List<LamiTableEntry> entries = getResultTable().getEntries();
        List<LamiTableEntryAspect> aspects = getResultTable().getTableClass().getAspects();

        /* Set the categories (aka the X axis) */
        LamiTableEntryAspect xAxisAspect = getAspectFromName(aspects, getChartModel().getXAxisColumn());

        String[] categories = entries.stream()
                .map(entry -> xAxisAspect.resolveString(entry))
                .toArray(String[]::new);
        fCategories = checkNotNull(categories);

        getChart().getAxisSet().getXAxis(0).enableCategory(true);

        /* Create bar series */
        boolean yIsLog = chartModel.yAxisIsLog();

        for (LamiTableEntryAspect aspect : getYAxisAspects()) {

            if (!aspect.isNumerical() || aspect.isTimeStamp()) {
                /* Only plot numerical aspects (parseDouble would fail below) */
                continue;
            }

            String name = aspect.getName();
            double[] ySeries;

            if (yIsLog) {
                ySeries = entries.stream()
                        .mapToDouble(entry -> aspect.resolveDouble(entry))
                        /* Log axis does not support 0 values. Clamp them to 0.9 */
                        .map(elem -> (elem < 0.9) ? 0.9 : elem)
                        .toArray();
            } else {
                ySeries = entries.stream()
                        .mapToDouble(entry -> aspect.resolveDouble(entry))
                        .toArray();
            }

            IBarSeries barSeries = (IBarSeries) getChart().getSeriesSet().createSeries(SeriesType.BAR, name);
            barSeries.setYSeries(ySeries);
        }
        setBarSeriesColors();

        Stream.of(getChart().getAxisSet().getYAxes()).forEach(axis -> axis.enableLogScale(yIsLog));

        /* Set the formatter on the Y axis */
        getChart().getAxisSet().getYAxis(0).getTick().setFormat(new DecimalUnitFormat());

        /* Adjust the chart range */
        getChart().getAxisSet().adjustRange();

        if (yIsLog) {
            /*
             * In case of a log Y axis, bump the X axis to hide the "fake" 0.9
             * values.
             */
            Range yRange = getChart().getAxisSet().getYAxis(0).getRange();
            getChart().getAxisSet().getYAxis(0).setRange(new Range(0.9, yRange.upper));
        }

        /* Once the chart is filled, refresh the axis labels */
        refreshDisplayLabels();

        /* Add mouse listener */
        getChart().getPlotArea().addListener(SWT.MouseDown, new LamiHistogramMouseDownListener());

        /* Custom Painter listener to highlight the current selection */
        getChart().getPlotArea().addPaintListener(new LamiHistogramPainterListener());

        /* Register to receive LamiSelectionUpdateSignal */
        TmfSignalManager.register(this);

    }


    private final class LamiHistogramMouseDownListener implements Listener {
        @Override
        public void handleEvent(@Nullable Event event) {
            if (event != null) {
                int xMouseLocation = event.x;
                int yMouseLocation = event.y;

                /* Reset selection state*/
                unsetSelection();

                ISeries[] series = getChart().getSeriesSet().getSeries();

                /* Iterate over all series, get the rectangle bounds for each categories,
                 * found the category index under the mouse.f
                 * Since categories map directly to the index of the fResulTable and that
                 * this table is immutable the index of the entry correspond the the categories
                 * index. Signal to all LamiViewer and LamiView the update of selection.
                 */
                for (ISeries oneSeries : series) {
                    IBarSeries barSerie = ((IBarSeries) oneSeries);
                    Rectangle[] recs =  barSerie.getBounds();

                    for (int j = 0; j < recs.length; j++) {
                        Rectangle rectangle = recs[j];
                        if (rectangle.contains(xMouseLocation, yMouseLocation)) {
                            /* Save the current selection internally */
                            setSelection(j);

                            /* Signal all Lami viewers & views of the selection */
                            LamiSelectionUpdateSignal signal = new LamiSelectionUpdateSignal(this,
                                    (int) barSerie.getXSeries()[j], checkNotNull(getResultTable().hashCode()));
                            TmfSignalManager.dispatchSignal(signal);

                            redraw();
                            return;
                        }
                    }
                }
                /* No rectangle under mouse redraw to update the graphic with no selection */
                redraw();
            }
        }
    }

    @Override
    protected void redraw() {
        setBarSeriesColors();
        super.redraw();
    }

    /**
     * Set the chart series colors according to the selection state.
     * Use light colors when a selection is present.
     */
    private void setBarSeriesColors() {
        Iterator<Color> colorsIt;

        if (isSelected()) {
            colorsIt = Iterators.cycle(LIGHT_COLORS);
        } else {
            colorsIt = Iterators.cycle(COLORS);
        }

        for (ISeries series : getChart().getSeriesSet().getSeries()) {
            ((IBarSeries) series).setBarColor(colorsIt.next());
        }
    }

    private final class LamiHistogramPainterListener implements PaintListener {
        @Override
        public void paintControl(@Nullable PaintEvent e) {
            if (e == null || !isSelected()) {
                return;
            }

            Iterator<Color> colorsIt = Iterators.cycle(COLORS);
            GC gc = e.gc;

            for (ISeries series : getChart().getSeriesSet().getSeries()) {
                Rectangle rectangle = ((IBarSeries) series).getBounds()[getSelection()];

                gc.setBackground(colorsIt.next());
                gc.fillRectangle(rectangle);

            }
        }
    }

    @Override
    protected void refreshDisplayLabels() {
        /* Only if we have at least 1 category */
        if (fCategories.length == 0) {
            return;
        }

        /* Only refresh if labels are visible */
        IAxis xAxis = getChart().getAxisSet().getXAxis(0);
        if (!xAxis.getTick().isVisible() || !xAxis.isCategoryEnabled()) {
            return;
        }

        /*
         * Shorten all the labels to 5 characters plus "…" when the longest
         * label length is more than 50% of the chart height.
         */

        Rectangle rect = getChart().getClientArea();
        int lengthLimit = (int) (rect.height * 0.40);

        GC gc = new GC(fParent);
        gc.setFont(xAxis.getTick().getFont());

        /* Find the longest category string */
        String longestString = Arrays.stream(fCategories)
                .max(Comparator.comparingInt(String::length))
                .orElse(fCategories[0]);

        /* Get the length and height of the longest label in pixels */
        Point pixels = gc.stringExtent(longestString);

        // Completely arbitrary
        int cutLen = 5;

        String[] displayCategories = new String[fCategories.length];
        if (pixels.x > lengthLimit) {
            /* We have to cut down some strings */
            for (int i = 0; i < fCategories.length; i++) {
                if (fCategories[i].length() > cutLen) {
                    displayCategories[i] = fCategories[i].substring(0, cutLen) + ELLIPSIS;
                } else {
                    displayCategories[i] = fCategories[i];
                }
            }
        } else {
            /* All strings should fit */
            displayCategories = Arrays.copyOf(fCategories, fCategories.length);
        }
        xAxis.setCategorySeries(displayCategories);

        /* Cleanup */
        gc.dispose();
    }

    @Override
    public void dispose() {
        TmfSignalManager.deregister(this);
        super.dispose();
    }
}
