package models;

/** Raised when user input (a file, a title) violates a rule. Message is user-facing. */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
