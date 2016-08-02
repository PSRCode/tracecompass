package org.eclipse.tracecompass.tmf.ui.views;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;



/**
 * @author Jonathan Rajotte-Julien
 * @since 2.1
 *
 */
public class CloneTmfViewAction extends Action {

    /**
     * Constructor
     */
    public CloneTmfViewAction() {
        super("Clone", IAction.AS_PUSH_BUTTON);

        setId("org.eclipse.linuxtools.tmf.ui.views.CloneTmfViewAction"); //$NON-NLS-1$
        setToolTipText("Clone the view");
//        setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(ITmfImageConstants.IMG_UI_PIN_VIEW));
    }


}
