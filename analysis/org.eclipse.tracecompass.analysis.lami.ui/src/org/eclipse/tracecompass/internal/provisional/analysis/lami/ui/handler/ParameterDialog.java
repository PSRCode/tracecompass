/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.internal.provisional.analysis.lami.ui.handler;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A simple input dialog for soliciting an input string from the user.
 * <p>
 * This concrete dialog class can be instantiated as is, or further subclassed as
 * required.
 * </p>
 */
@NonNullByDefault({})
class ParameterDialog extends Dialog {

    private static final Color GRAY_COLOR = Display.getCurrent().getSystemColor(SWT.COLOR_DARK_GRAY);

    private String title;
    private String message;
    private String value = "";//$NON-NLS-1$
    private IInputValidator validator;
    private Button okButton;
    private Text text;
    private Text errorMessageText;
    private String errorMessage;

    private Text baseCommandText;
    private final String baseCommand;

    public ParameterDialog(Shell parentShell,
            String dialogTitle,
            String dialogMessage,
            String baseCommand,
            IInputValidator validator) {
        super(parentShell);
        this.title = dialogTitle;
        message = dialogMessage;
        value = "";//$NON-NLS-1$
        this.validator = validator;
        this.baseCommand = baseCommand;
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) {
            value = text.getText();
        } else {
            value = null;
        }
        super.buttonPressed(buttonId);
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        if (title != null) {
            shell.setText(title);
        }
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        okButton = createButton(parent, IDialogConstants.OK_ID,
                IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID,
                IDialogConstants.CANCEL_LABEL, false);
        text.setFocus();
        if (value != null) {
            text.setText(value);
            text.selectAll();
        }
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        // create composite
        Composite composite = (Composite) super.createDialogArea(parent);

        Label label = new Label(composite, SWT.WRAP);
        label.setText(Messages.ParameterDialog_BaseCommand + ':');

        baseCommandText = new Text(composite, getInputTextStyle() | SWT.READ_ONLY);
        baseCommandText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
                | GridData.HORIZONTAL_ALIGN_FILL));
        baseCommandText.setText(baseCommand);
        baseCommandText.setForeground(GRAY_COLOR);

        // create message
        if (message != null) {
            label = new Label(composite, SWT.WRAP);
            label.setText(message);
            GridData data = new GridData(GridData.GRAB_HORIZONTAL
                    | GridData.GRAB_VERTICAL | GridData.HORIZONTAL_ALIGN_FILL
                    | GridData.VERTICAL_ALIGN_CENTER);
            data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
            label.setLayoutData(data);
            label.setFont(parent.getFont());
        }



        text = new Text(composite, getInputTextStyle());
        text.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
                | GridData.HORIZONTAL_ALIGN_FILL));
        text.addModifyListener(e -> validateInput());
        errorMessageText = new Text(composite, SWT.READ_ONLY | SWT.WRAP);
        errorMessageText.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
                | GridData.HORIZONTAL_ALIGN_FILL));
        errorMessageText.setBackground(errorMessageText.getDisplay()
                .getSystemColor(SWT.COLOR_WIDGET_BACKGROUND));
        setErrorMessage(errorMessage);

        applyDialogFont(composite);
        return composite;
    }

    /**
     * Returns the error message label.
     *
     * @return the error message label
     * @deprecated use setErrorMessage(String) instead
     */
    @Deprecated
    protected Label getErrorMessageLabel() {
        return null;
    }

    /**
     * Returns the ok button.
     *
     * @return the ok button
     */
    protected Button getOkButton() {
        return okButton;
    }

    /**
     * Returns the text area.
     *
     * @return the text area
     */
    protected Text getText() {
        return text;
    }

    /**
     * Returns the validator.
     *
     * @return the validator
     */
    protected IInputValidator getValidator() {
        return validator;
    }

    /**
     * Returns the string typed into this input dialog.
     *
     * @return the input string
     */
    public String getValue() {
        return value;
    }

    /**
     * Validates the input.
     * <p>
     * The default implementation of this framework method delegates the request
     * to the supplied input validator object; if it finds the input invalid,
     * the error message is displayed in the dialog's message line. This hook
     * method is called whenever the text changes in the input field.
     * </p>
     */
    protected void validateInput() {
        String errMsg = null;
        if (validator != null) {
            errMsg = validator.isValid(text.getText());
        }
        setErrorMessage(errMsg);
    }

    /**
     * Sets or clears the error message.
     * If not <code>null</code>, the OK button is disabled.
     *
     * @param errorMessage
     *            the error message, or <code>null</code> to clear
     * @since 3.0
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        if (errorMessageText != null && !errorMessageText.isDisposed()) {
            errorMessageText.setText(errorMessage == null ? " \n " : errorMessage); //$NON-NLS-1$
            boolean hasError = errorMessage != null && (StringConverter.removeWhiteSpaces(errorMessage)).length() > 0;
            errorMessageText.setEnabled(hasError);
            errorMessageText.setVisible(hasError);
            errorMessageText.getParent().update();
            Control button = getButton(IDialogConstants.OK_ID);
            if (button != null) {
                button.setEnabled(errorMessage == null);
            }
        }
    }

    /**
     * Returns the style bits that should be used for the input text field.
     * Defaults to a single line entry. Subclasses may override.
     *
     * @return the integer style bits that should be used when creating the
     *         input text
     *
     * @since 3.4
     */
    protected int getInputTextStyle() {
        return SWT.SINGLE | SWT.BORDER;
    }
}
