/*******************************************************************************
 * Copyright (c) 2015 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types;

class LamiString extends LamiData {

    private final String fValue;

    public LamiString(String value) {
        fValue = value;
    }

    public String getValue() {
        return fValue;
    }

    @Override
    public String toString() {
        return String.valueOf(fValue);
    }
}
