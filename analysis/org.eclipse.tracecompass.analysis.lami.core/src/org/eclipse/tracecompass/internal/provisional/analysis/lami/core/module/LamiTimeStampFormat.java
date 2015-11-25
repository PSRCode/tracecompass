/*******************************************************************************
 * Copyright (c) 2015 EfficiOS inc. and others
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.module;

import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimestampFormat;

/**
 * Formatter for time stamps
 */
public class LamiTimeStampFormat extends SimpleDateFormat {

    private static final long serialVersionUID = 4285447886537779762L;

    @Override
    public StringBuffer format(@Nullable Date date,  @Nullable StringBuffer toAppendTo, @Nullable FieldPosition fieldPosition) {
        if (date != null && toAppendTo != null) {
            long time = date.getTime();
            toAppendTo.append(TmfTimestampFormat.getDefaulTimeFormat().format(time));
            return toAppendTo;
        }
        return new StringBuffer();
    }
}
