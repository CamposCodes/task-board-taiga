package com.r2da.taskboard.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Problems {

    private Problems() {
    }

    public static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    public static ResponseStatusException unauthorized(String detail) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, detail);
    }

    public static ResponseStatusException forbidden(String detail) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, detail);
    }

    public static ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    public static ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
