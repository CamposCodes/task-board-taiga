package com.r2da.taskboard.projects;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum Perm {
    view_project,
    view_milestones, add_milestone, modify_milestone, delete_milestone,
    view_us, add_us, modify_us, comment_us, delete_us,
    view_tasks, add_task, modify_task, comment_task, delete_task,
    view_issues, add_issue, modify_issue, comment_issue, delete_issue,
    view_wiki_pages, add_wiki_page, modify_wiki_page, delete_wiki_page;

    public static final Set<Perm> ALL = Collections.unmodifiableSet(EnumSet.allOf(Perm.class));

    public static final Set<Perm> READ = Collections.unmodifiableSet(Arrays.stream(values()).filter(Perm::isView)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(Perm.class))));

    public boolean isView() {
        return name().startsWith("view_");
    }

    public static Perm of(String action, Kind kind) {
        String plural = action.equals("view") && kind != Kind.us ? "s" : "";
        return valueOf(action + "_" + kind.name() + plural);
    }

    public static Set<Perm> parse(String[] names) {
        Set<Perm> perms = EnumSet.noneOf(Perm.class);
        for (String name : names) {
            try {
                perms.add(valueOf(name));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return perms;
    }
}
