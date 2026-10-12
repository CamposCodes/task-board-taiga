package com.r2da.taskboard.users;

public record UserRef(Long id, String username, String fullName) {

    public static UserRef of(User user) {
        return user == null ? null : new UserRef(user.getId(), user.getUsername(), user.getFullName());
    }
}
