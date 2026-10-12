package com.r2da.taskboard.common;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O registro foi alterado por outra pessoa. Recarregue e tente de novo.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A operação viola uma restrição dos dados: registro duplicado ou ainda em uso.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail unauthenticated() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Acesso negado.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        body.setDetail(ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; ")));
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
