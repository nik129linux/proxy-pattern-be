package com.proxypattern.web;

import com.proxypattern.error.BusinessRuleException;
import com.proxypattern.error.InvalidParamsException;
import com.proxypattern.error.ResourceNotFoundException;
import com.proxypattern.error.ServiceException;
import com.proxypattern.error.SimulatedFailureException;
import com.proxypattern.error.UnknownOperationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates service errors into the shared error shape. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownOperationException.class)
    public ResponseEntity<Map<String, String>> unknownOperation(ServiceException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(ServiceException e) {
        return error(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(InvalidParamsException.class)
    public ResponseEntity<Map<String, String>> invalidParams(ServiceException e) {
        return error(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, String>> businessRule(ServiceException e) {
        return error(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(SimulatedFailureException.class)
    public ResponseEntity<Map<String, String>> simulatedFailure(ServiceException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadableBody(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "INVALID_PARAMS", "message", "malformed JSON body"));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, ServiceException e) {
        return ResponseEntity.status(status).body(Map.of("error", e.code(), "message", e.getMessage()));
    }
}
