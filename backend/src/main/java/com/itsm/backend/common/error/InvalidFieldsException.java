package com.itsm.backend.common.error;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * A 400 that, like bean validation failures, lists the offending fields in {@code errors}. For checks that depend on
 * stored data (e.g. answers checked against a catalog item's form) and so cannot be annotations on the request.
 */
public class InvalidFieldsException extends ApiException {

    /** One problem with one field; {@code field} is the request path, e.g. {@code answers.model}. */
    public record FieldProblem(String field, String message) {
    }

    private final List<FieldProblem> errors;

    public InvalidFieldsException(String code, String message, List<FieldProblem> errors) {
        super(HttpStatus.BAD_REQUEST, code, message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldProblem> getErrors() {
        return errors;
    }
}
