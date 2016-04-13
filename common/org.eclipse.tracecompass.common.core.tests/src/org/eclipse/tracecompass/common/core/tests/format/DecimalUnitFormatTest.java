/*******************************************************************************
 * Copyright (c) 2016 EfficiOS inc, Michael Jeanson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.common.core.tests.format;

import static org.junit.Assert.assertEquals;

import java.text.Format;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.common.core.format.DecimalUnitFormat;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test the {@link DecimalUnitFormat} class
 *
 * @author Michael Jeanson
 */
@RunWith(Parameterized.class)
public class DecimalUnitFormatTest {

    private static final @NonNull Format FORMATTER = new DecimalUnitFormat();

    private final @NonNull Number fNumValue;
    private final @NonNull String fExpected;

    /**
     * Constructor
     *
     * @param value
     *            The numeric value to format
     * @param expected
     *            The expected formatted result
     */
    public DecimalUnitFormatTest(@NonNull Number value, @NonNull String expected) {
        fNumValue = value;
        fExpected = expected;
    }

    /**
     * @return The arrays of parameters
     */
    @Parameters(name = "{index}: {0}")
    public static Iterable<Object[]> getParameters() {
        return Arrays.asList(new Object[][] {
                { 0, "0" },
                { 3, "3" },
                { 975, "975" },
                { 1000, "1 k" },
                { 4000, "4 k" },
                { -4000, "-4 k" },
                { 4000L, "4 k" },
                { 4000.0, "4 k" },
                { 12345678, "12.3 M" },
                { Integer.MAX_VALUE, "2.1 G" },
                { Integer.MIN_VALUE, "-2.1 G" },
                { Long.MAX_VALUE, "9223.4 P" },
                { 98765432.123456, "98.8 M" },
                { -98765432.123456, "-98.8 M" },
                { 555555555555L, "555.6 G" },
                { 555555555555555L, "555.6 T" },
        });
    }

    /**
     * Test the {@link Format#format(Object)} method
     */
    @Test
    public void testFormat() {
        assertEquals("format value", fExpected, FORMATTER.format(fNumValue));
    }
}
