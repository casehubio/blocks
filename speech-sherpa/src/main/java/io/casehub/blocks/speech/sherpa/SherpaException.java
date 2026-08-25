package io.casehub.blocks.speech.sherpa;

public class SherpaException extends RuntimeException {
    public SherpaException(String message) {
        super(message);
    }

    public SherpaException(String message, Throwable cause) {
        super(message, cause);
    }
}
