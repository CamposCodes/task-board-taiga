package com.r2da.taskboard.projects;

public record StatusRef(Long id, String name, String color, boolean isClosed) {

    public static StatusRef of(Status s) {
        return new StatusRef(s.getId(), s.getName(), s.getColor(), s.isClosed());
    }
}
