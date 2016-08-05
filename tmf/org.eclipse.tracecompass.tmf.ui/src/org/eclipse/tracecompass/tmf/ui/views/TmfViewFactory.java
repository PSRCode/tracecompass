package org.eclipse.tracecompass.tmf.ui.views;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * @author Jonathan Rajotte-Julien
 * @since 2.1
 *
 */
public class TmfViewFactory {

    private static TmfViewFactory fInstance = null;

    private Map<String, TmfView> clonedViewData = new HashMap<>();

    private TmfViewFactory() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @return The singleton instance
     */
    public static TmfViewFactory getInstance() {
        if (fInstance == null) {
            fInstance = new TmfViewFactory();
        }

        return fInstance;
    }

    /**
     *
     * @param view
     *          The view to be  cloned
     * @return
     * @throws PartInitException
     */
    public void clone(TmfView view) throws PartInitException {

        if (view == null) {
            return;
        }

        String primaryId = view.getViewSite().getId();
        String secondaryId = UUID.randomUUID().toString();

        clonedViewData.put(primaryId + '.' + secondaryId , view);

        IWorkbench workbench = PlatformUI.getWorkbench();
        IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
        IWorkbenchPage page = workbenchWindow.getActivePage();
        page.showView(primaryId, secondaryId, IWorkbenchPage.VIEW_VISIBLE);
    }

    /**
     * @param view
     *          The potentially cloned view
     * @return The original view
     */
    public @Nullable TmfView getOrigin(TmfView view) {
        IViewSite viewSite = view.getViewSite();
        if (viewSite == null) {
            return null;
        }

        String primaryId = view.getViewSite().getId();
        String secondaryId = view.getViewSite().getSecondaryId();

        return clonedViewData.get( primaryId + '.' + secondaryId);
    }
}
