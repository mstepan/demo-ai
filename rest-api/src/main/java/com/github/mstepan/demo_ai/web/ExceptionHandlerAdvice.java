package com.github.mstepan.demo_ai.web;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Exception mapping using https://datatracker.ietf.org/doc/html/rfc7807 as an error response
 * standard.
 */
@RestControllerAdvice
public class ExceptionHandlerAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {

        var problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");

        problemDetail.setType(URI.create("demo-ai:bad-request"));

        var validationMessages =
                ex.getBindingResult().getAllErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .toList();

        problemDetail.setProperty("invalid-params", validationMessages);
        return problemDetail;
    }
}
