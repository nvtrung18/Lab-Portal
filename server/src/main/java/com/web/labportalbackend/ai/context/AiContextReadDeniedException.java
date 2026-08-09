package com.web.labportalbackend.ai.context;

/** Deliberately generic so a changed authorization/read state cannot be enumerated. */
public class AiContextReadDeniedException extends RuntimeException {
    public AiContextReadDeniedException() {
        super("AI context is unavailable");
    }
}
