/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc, Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * UI Model of a LAMI chart. This object should contain all the information
 * needed to create a chart in the GUI, independently of the actual chart
 * implementation.
 *
 * @author Alexandre Montplaisir
 */
public class LamiChartModel {

    /**
     * Supported types of charts
     */
    public enum ChartType {
        /** Histogram */
        HISTOGRAM("Histogram"), //$NON-NLS-1$

        /** XY scatter chart */
        XY_SCATTER("Scatter"), //$NON-NLS-1$

        /** Pie chart FIXME NYI */
        PIE_CHART("Pie"); //$NON-NLS-1$

        private final String fText;

        private ChartType(final String text) {
             fText = text;
        }

        @Override
        public String toString() {
            return fText;
        }
    }

    private final ChartType fType;
    private final String fName;
    private final String fXAxisColumn;
    private final List<String> fSeriesColumns;
    private final boolean fXAxisIsLog;
    private final boolean fYAxisIsLog;


    /**
     * Constructor
     *
     * @param type
     *            The type of chart
     * @param name
     *            The name of the chart
     * @param xAxisColumn
     *            The title of column used for the X axis
     * @param seriesColumns
     *            The titles of the columns used for the series
     * @param xAxisIsLog
     *            If the X-axis is log scale or not
     * @param yAxisIsLog
     *            If the Y-axis is log scale or not
     */
    public LamiChartModel(ChartType type, String name, String xAxisColumn, List<String> seriesColumns,
            boolean xAxisIsLog, boolean yAxisIsLog) {
        fType = type;
        fName = name;
        fXAxisColumn = xAxisColumn;
        fSeriesColumns = ImmutableList.copyOf(seriesColumns);
        fXAxisIsLog = xAxisIsLog;
        fYAxisIsLog = yAxisIsLog;
    }

    /**
     * Get the chart type.
     *
     * @return The chart type
     */
    public ChartType getChartType() {
        return fType;
    }

    /**
     * Get the chart's name.
     *
     * @return The chart name
     */
    public String getName() {
        return fName;
    }

    /**
     * Get the name of the column used for the X-axis.
     *
     * @return The colum used for the X-axis
     */
    public String getXAxisColumn() {
        return fXAxisColumn;
    }

    /**
     * Get the names of the columns used for the series.
     *
     * @return The columns used for the series
     */
    public List<String> getSeriesColumns() {
        return fSeriesColumns;
    }

    /**
     * Return if the X-axis should use a log scale.
     *
     * @return If the x-axis is log scale
     */
    public boolean xAxisIsLog() {
        return fXAxisIsLog;
    }

    /**
     * Return if the Y-axis should use a log scale.
     *
     * @return If Y-axis is log scale
     */
    public boolean yAxisIsLog() {
        return fYAxisIsLog;
    }

}
