/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Jonathan Rajotte-Julien
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.viewers;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.tracecompass.common.core.format.DecimalUnitFormat;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.aspect.LamiTableEntryAspect;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiChartModel.ChartType;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiResultTable;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTableEntry;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module.LamiTimeStampFormat;
import org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.signals.LamiSelectionUpdateSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.swtchart.IAxisTick;
import org.swtchart.ILineSeries;
import org.swtchart.ISeries;
import org.swtchart.ISeries.SeriesType;
import org.swtchart.LineStyle;
import org.swtchart.Range;

import com.google.common.collect.Iterators;

/**
 * XY Scatter chart viewer for Lami views
 *
 * @author Jonathan Rajotte-Julien
 */
public class LamiScatterViewer extends LamiXYChartViewer {

    /**
     * Constructor
     *
     * @param parent
     *            parent
     * @param resultTable
     *            Result table populating this chart
     * @param graphModel
     *            Model of this chart
     */
    public LamiScatterViewer(Composite parent, LamiResultTable resultTable, LamiChartModel graphModel) {
        super(parent, resultTable, graphModel);
        if (getChartModel().getChartType() != ChartType.XY_SCATTER) {
            throw new IllegalStateException();
        }

        List<LamiTableEntry> entries = getResultTable().getEntries();
        List<LamiTableEntryAspect> aspects = getResultTable().getTableClass().getAspects();

        /* Get the aspect for the X axis*/
        LamiTableEntryAspect xAxisAspect = getAspectFromName(aspects, getChartModel().getXAxisColumn());

        /* Scatter chart does not support non Numerical X axis */
        if (!xAxisAspect.isNumerical()) {
            throw new IllegalStateException();
        }

        /* Basic plot formatting based on the aspect type */
        if (xAxisAspect.isTimeStamp()) {
            /* Only apply a custom format on Timestamp */
            IAxisTick xTick = getChart().getAxisSet().getXAxis(0).getTick();
            xTick.setFormat(new LamiTimeStampFormat());
        }

        /* Create X series */
        double[] xSerie;
        boolean xIsLog = graphModel.xAxisIsLog();

        if (xIsLog) {
            /* Log axis does not support 0 values. Clamp them to 0.9 */
            xSerie = entries.stream()
                    .mapToDouble(entry -> xAxisAspect.resolveDouble(entry))
                    .map(elem -> (elem < 0.9) ? 0.9 : elem)
                    .toArray();
        } else {
            xSerie = entries.stream()
                    .mapToDouble(entry -> xAxisAspect.resolveDouble(entry))
                    .toArray();
        }

        /* Create Y series */
        boolean yIsLog = graphModel.yAxisIsLog();

        for (String colName : getChartModel().getSeriesColumns()) {
            LamiTableEntryAspect aspect = getAspectFromName(aspects, colName);
            if (!aspect.isNumerical()) {
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

            ILineSeries scatterSeries = (ILineSeries) getChart().getSeriesSet().createSeries(SeriesType.LINE, name);
            scatterSeries.setLineStyle(LineStyle.NONE);
            scatterSeries.setXSeries(xSerie);
            scatterSeries.setYSeries(ySeries);

            /* TODO: change color per series */



        }
        setLineSeriesColor();

        getChart().getAxisSet().adjustRange();

        /* Put log scale if necessary */
        Stream.of(getChart().getAxisSet().getYAxes()).forEach(axis -> axis.enableLogScale(yIsLog));
        Stream.of(getChart().getAxisSet().getXAxes()).forEach(axis -> axis.enableLogScale(xIsLog));

        /* Set the formatter on the Y axis */
        getChart().getAxisSet().getYAxis(0).getTick().setFormat(new DecimalUnitFormat());

        if (yIsLog) {
            /*
             * In case of a log Y axis, bump the X axis to hide the "fake" 0.9
             * values.
             */
            Range yRange = getChart().getAxisSet().getYAxis(0).getRange();
            getChart().getAxisSet().getYAxis(0).setRange(new Range(0.9, yRange.upper));
        }

        getChart().getPlotArea().addListener(SWT.MouseDown,new LamiScatterMouseDownListener());


        getChart().getPlotArea().addPaintListener(new LamiScatterPainterListener());

        /* Register to receive LamiSelectionUpdateSignal */
        TmfSignalManager.register(this);
    }

    /**
     * Set the chart series colors.
     */
    private void setLineSeriesColor() {
        Iterator<Color> colorsIt;

        colorsIt = Iterators.cycle(COLORS);

        for (ISeries series : getChart().getSeriesSet().getSeries()) {
            ((ILineSeries) series).setSymbolColor((colorsIt.next()));
            /* Generate initial array of Color to enable per point color change on selection in the future */
            ArrayList<Color> colors = new ArrayList<>();
            for (int i = 0; i < series.getXSeries().length; i++) {
                Color color = ((ILineSeries) series).getSymbolColor();
                colors.add(checkNotNull(color));
            }
            ((ILineSeries) series).setSymbolColors(colors.toArray(new Color[colors.size()]));
        }

    }

    private final class LamiScatterMouseDownListener implements Listener {
        @Override
        public void handleEvent(@Nullable Event event) {
            if (event == null) {
                return;
            }

            ISeries[] series = getChart().getSeriesSet().getSeries();

            /* Reset selection */
            unsetSelection();

            for (ISeries oneSeries : series) {
                ILineSeries lineSerie = (ILineSeries) oneSeries;
                for (int xSeriesIndex = 0; xSeriesIndex < lineSerie.getXSeries().length; xSeriesIndex++) {
                    org.eclipse.swt.graphics.Point dataPoint = lineSerie.getPixelCoordinates(xSeriesIndex);

                    /*
                     * Find the distance between the data point and the mouse location
                     * and compare it to the symbol size so when a user click on a symbol it select it.
                     */
                    double distance = Math.hypot(dataPoint.x - event.x, dataPoint.y - event.y);
                    if (distance < lineSerie.getSymbolSize()) {
                        setSelection(xSeriesIndex);

                        /* Signal all Lami viewers & views of the selection */
                        LamiSelectionUpdateSignal signal = new LamiSelectionUpdateSignal(this,
                                xSeriesIndex, checkNotNull(getResultTable().hashCode()));
                        TmfSignalManager.dispatchSignal(signal);

                        redraw();
                    }
                }
            }

            redraw();
        }
    }

    private final class LamiScatterPainterListener implements PaintListener {
        @Override
        public void paintControl(@Nullable PaintEvent e) {
            if (e == null || !isSelected()) {
                return;
            }
            GC gc = e.gc;
            gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_MAGENTA));

            gc.setLineWidth(1);
            gc.setLineStyle(SWT.LINE_SOLID);
            for (ISeries series : getChart().getSeriesSet().getSeries()) {
                /* Generate a cross for each selected dot */
                org.eclipse.swt.graphics.Point point = series.getPixelCoordinates(getSelection());
                /* Vertical line */
                gc.drawLine(point.x, 0, point.x, getChart().getPlotArea().getSize().y);
                /* Horizontal line */
                gc.drawLine(0, point.y, getChart().getPlotArea().getSize().x, point.y);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.viewers.LamiXYChartViewer#refreshDisplayLabels()
     */
    @Override
    protected void refreshDisplayLabels() {

    }

    @Override
    public void dispose() {
        TmfSignalManager.deregister(this);
        super.dispose();
    }

}
