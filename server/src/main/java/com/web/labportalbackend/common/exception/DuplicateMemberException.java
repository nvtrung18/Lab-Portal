package com.web.labportalbackend.common.exception;

public class DuplicateMemberException extends RuntimeException {

    public DuplicateMemberException(Long groupId, Long userId) {
        super(String.format("User %d is already a member of group %d", userId, groupId));
    }
}
