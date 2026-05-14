package com.web.labportalbackend.common.exception;

public class InvalidAssigneeException extends RuntimeException {

    public InvalidAssigneeException(Long assigneeId, Long groupId) {
        super(String.format("User %d is not a member of research group %d", assigneeId, groupId));
    }
}
