/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Philippe Proulx
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.core.types;

class LamiSystemCall extends LamiString {
    private final String fString;

    public LamiSystemCall(String value) {
        super(value);

        if (value.endsWith("()")) { //$NON-NLS-1$
            fString = value;
        } else {
            fString = value + "()"; //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        return fString;
    }
}
