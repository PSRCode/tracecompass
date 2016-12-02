/*******************************************************************************
 * Copyright (c) 2016 EfficiOS Inc., Alexandre Montplaisir
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.tmf.ctf.ui.streamintersection;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.osgi.util.NLS;

/**
 * Package messages
 *
 * @author Alexandre Montplaisir
 * @noreference Messages class
 */
@SuppressWarnings("javadoc")
@NonNullByDefault({})
public class Messages extends NLS {

    private static final String BUNDLE_NAME = Messages.class.getPackage().getName() + ".messages"; //$NON-NLS-1$

    public static String StreamIntersection_JobName;
    public static String StreamIntersection_DirectoryChooser_DialogTitle;
    public static String StreamIntersection_InvalidDirectory_DialogTitle;
    public static String StreamIntersection_InvalidDirectory_DialogText;
    public static String StreamIntersection_NoWriteAccess_DialogText;

    static {
        NLS.initializeMessages(BUNDLE_NAME, Messages.class);
    }

    private Messages() {
    }
}
