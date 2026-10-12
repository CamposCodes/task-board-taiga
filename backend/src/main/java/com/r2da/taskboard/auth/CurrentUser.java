package com.r2da.taskboard.auth;

import java.util.Set;

import com.r2da.taskboard.common.Problems;

public record CurrentUser(Long id, String username, boolean superuser, Long tokenId, Set<String> scopes) {

    public boolean viaToken() {
        return tokenId != null;
    }

    public void requireSuperuser() {
        if (!superuser) {
            throw Problems.forbidden("Apenas administradores do sistema.");
        }
    }

    public void requireSession() {
        if (viaToken()) {
            throw Problems.forbidden("Operação não permitida com token de API.");
        }
    }
}
