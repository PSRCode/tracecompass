/**********************************************************************
 * Copyright (c) 2016 EfficiOS Inc.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.tmf.ui.views;

import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.google.common.annotations.VisibleForTesting;

/**
 * Factory for TmfView.
 *
 * @author Jonathan Rajotte Julien
 * @since 2.2
 */
public final class TmfViewFactory {

    /**
     * The separator used for secondary id internal use. This allow to have
     * multiple level of information inside the secondary id.
     */
    @VisibleForTesting
    public static final String INTERNAL_SECONDARY_ID_SEPARATOR = "&"; //$NON-NLS-1$

    /**
     * Empty constructor
     */
    private TmfViewFactory() {}

    /**
     * Create a new view. <br>
     *
     * If a view with the corresponding id already exist and no suffix were
     * added the existing view will be given focus.
     *
     * @param viewId
     *            The id of the view to be created. <br>
     *            Format: <primary_id>[:secondary_id][&third_id]..[&n_id]
     * @param generateSuffix
     *            Add a generated suffix id (UUID). This allow multiple view of
     *            the same id to be displayed.
     * @return
     *          NULL if an error occurred.
     *          The view instance.
     */
    @NonNullByDefault
    public static @Nullable IViewPart newView(String viewId, boolean generateSuffix) {
        IViewPart viewPart = null;
        String primaryId = null;
        String secondaryId = null;

        /* Parse the view id */
        if (viewId.contains(TmfView.PRIMARY_SECONDARY_ID_SEPARATOR)) {
            int index = viewId.indexOf(TmfView.PRIMARY_SECONDARY_ID_SEPARATOR);

            primaryId = viewId.substring(0, index);
            secondaryId = getBaseSecId(viewId.substring(index + 1));

        } else {
            primaryId = viewId;
        }

        if (generateSuffix) {
            if (secondaryId == null) {
                secondaryId = UUID.randomUUID().toString();
            } else {
                secondaryId += INTERNAL_SECONDARY_ID_SEPARATOR + UUID.randomUUID().toString();
            }
        }

        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
        IWorkbenchPage page = workbenchWindow.getActivePage();
        try {
            viewPart = page.showView(primaryId, secondaryId, IWorkbenchPage.VIEW_ACTIVATE);
            page.activate(viewPart);
        } catch (PartInitException e) {
            /* Simply return null on error */
        }

        return viewPart;
    }

    /**
     * Parse a secondary id and return the base secondary id minus any generated
     * suffix (UUID).
     *
     * @param secId
     *            A view secondary id
     * @return NULL when the passed string is a UUID. <br>
     *         The base secondary id
     */
    public static String getBaseSecId(String secId) {
        if (secId == null) {
            return null;
        }

        if (!secId.contains(INTERNAL_SECONDARY_ID_SEPARATOR)) {
            if (isUUID(secId)) {
                return null;
            }
            return secId;
        }

        int lastIndexSepartor = secId.lastIndexOf(INTERNAL_SECONDARY_ID_SEPARATOR);

        /**
         * Validate that the right side of the separator is a UUID since the
         * separator could be a valid value from the base secondary id.
         */
        String potentialUUID = secId.substring(lastIndexSepartor + 1);

        if (!isUUID(potentialUUID)) {
            return secId;
        }

        return secId.substring(0, lastIndexSepartor);
    }

    /**
     * Utility method for testing if a string is a valid full length UUID.
     * <br>
     * <pre>
     * e.g:
     *     9eaf1840-8a87-4314-a8b7-03e3eccf4766 -> true
     *     1-1-1-1-1 -> false
     * </pre>
     *
     * @param uuid
     *            The string to test
     * @return If the passed string is a UUID
     */
    private static boolean isUUID(String uuid) {
        if (uuid == null) {
            return false;
        }

        try {
            /*
             * UUID.fromString does not check for length wise valid UUID only the
             * UUID form so check if the reverse operation is valid.
             */
            UUID fromStringUUID = UUID.fromString(uuid);
            String toStringUUID = fromStringUUID.toString();
            return toStringUUID.equals(uuid);
        } catch (IllegalArgumentException e) {
            /**
             * The substring is not a UUID. Assume that the separator come from
             * the initial secondaryId.
             */
             return false;
        }
    }
}
