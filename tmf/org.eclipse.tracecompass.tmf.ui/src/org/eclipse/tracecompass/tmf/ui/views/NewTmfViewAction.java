/**********************************************************************
 * Copyright (c) 2016 EfficiOS Inc.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.tmf.ui.views;

import java.text.MessageFormat;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.tracecompass.internal.tmf.ui.Messages;

/**
 * Action to instantiate a new instance of views that support it.
 * @author Jonathan Rajotte Julien
 * @since 2.2
 */
public class NewTmfViewAction extends Action {

    /**
     * Creates a new <code>NewTmfViewAction</code>.
     *
     * @param view
     *            The view for which the action is created
     */
    public NewTmfViewAction(TmfView view) {
        super(MessageFormat.format(Messages.TmfView_NewTmfViewNameText, view.getTitle().toLowerCase()), IAction.AS_PUSH_BUTTON);
        setToolTipText(MessageFormat.format(Messages.TmfView_NewTmfViewToolTipText, view.getTitle()));
    }
}
