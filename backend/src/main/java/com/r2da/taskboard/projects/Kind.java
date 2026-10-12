package com.r2da.taskboard.projects;

import com.r2da.taskboard.common.Problems;

public enum Kind {
    us("userstories", "História de usuário"), task("tasks", "Tarefa"), issue("issues", "Problema");

    public final String path;

    public final String label;

    Kind(String path, String label) {
        this.path = path;
        this.label = label;
    }

    public static Kind ofPath(String path) {
        for (Kind kind : values()) {
            if (kind.path.equals(path)) {
                return kind;
            }
        }
        throw Problems.notFound("Recurso não encontrado.");
    }
}
