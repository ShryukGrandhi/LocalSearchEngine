package views;

import java.awt.Component;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import models.InvalidDocumentException;
import models.StorageException;

/** Shared error and information dialogs. */
final class Dialogs {

    private static final Logger LOG = Logger.getLogger(Dialogs.class.getName());

    private Dialogs() {
    }

    static void showError(Component parent, String title, Throwable error) {
        if (!isExpected(error)) {
            LOG.log(Level.WARNING, title, error);
        }
        JOptionPane.showMessageDialog(parent, messageOf(error), title, JOptionPane.ERROR_MESSAGE);
    }

    static void showInfo(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    static String messageOf(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Unexpected error: " + error.getClass().getSimpleName();
        }
        return message;
    }

    /** Errors whose message is already written for the user and need no log entry. */
    private static boolean isExpected(Throwable error) {
        return error instanceof InvalidDocumentException
                || error instanceof StorageException
                || error instanceof IllegalArgumentException;
    }
}
