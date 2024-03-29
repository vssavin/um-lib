package io.github.vssavin.umlib.exception;

/**
 * @author vssavin on 08.01.2022
 */
public class EmailNotFoundException extends RuntimeException{

    public EmailNotFoundException(String message) {
        super(message);
    }

    public EmailNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
