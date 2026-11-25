package com.finsight.service.importing;

import com.finsight.api.exception.IngestionException;

/** The PDF is encrypted and no (or a wrong) password was supplied. */
public class PasswordRequiredException extends IngestionException {

    private final boolean passwordWasSupplied;

    public PasswordRequiredException(boolean passwordWasSupplied) {
        super(passwordWasSupplied
            ? "That password didn't open the PDF. Check it and try again."
            : "This PDF is password-protected. Enter its password to import it.");
        this.passwordWasSupplied = passwordWasSupplied;
    }

    public boolean passwordWasSupplied() {
        return passwordWasSupplied;
    }
}
