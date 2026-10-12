package com.r2da.taskboard.items;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.r2da.taskboard.projects.Kind;
import com.r2da.taskboard.projects.StatusRef;
import com.r2da.taskboard.users.UserRef;

public record ItemDto(Long id, Long projectId, Kind type, int ref, String subject, String description,
        StatusRef status, MilestoneRef milestone, ParentRef parent, BigDecimal points, UserRef assignedTo,
        List<UserRef> assignedUsers, List<UserRef> watchers, UserRef createdBy, List<String> tags,
        LocalDate dueDate, ChoiceRef issueType, ChoiceRef severity, ChoiceRef priority, int boardOrder,
        int listOrder, boolean isBlocked, String blockedNote, boolean teamRequirement, boolean clientRequirement,
        boolean isIocaine, int version, Instant createdAt, Instant modifiedAt, Instant finishedAt, long tasksTotal,
        long tasksDone, long commentsCount, long attachmentsCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ItemDto> tasks) {

    public record MilestoneRef(Long id, String name, String slug) {
    }

    public record ParentRef(Long id, int ref, String subject) {
    }

    public record ChoiceRef(Long id, String name, String color) {
    }

    ItemDto withTasks(List<ItemDto> tasks) {
        return new ItemDto(id, projectId, type, ref, subject, description, status, milestone, parent, points,
                assignedTo, assignedUsers, watchers, createdBy, tags, dueDate, issueType, severity, priority,
                boardOrder, listOrder, isBlocked, blockedNote, teamRequirement, clientRequirement, isIocaine, version,
                createdAt, modifiedAt, finishedAt, tasksTotal, tasksDone, commentsCount, attachmentsCount, tasks);
    }
}
