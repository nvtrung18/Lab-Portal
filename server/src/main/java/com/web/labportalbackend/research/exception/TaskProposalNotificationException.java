package com.web.labportalbackend.research.exception;

public class TaskProposalNotificationException extends RuntimeException {

    public TaskProposalNotificationException(Throwable cause) {
        super("Task proposal notification failed", cause);
    }
}
