package ru.javaops.topjava.web;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.javaops.topjava.error.AppException;
import ru.javaops.topjava.error.DataConflictException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final MessageSource messageSource;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.updateAndGetBody(this.messageSource, LocaleContextHolder.getLocale());
        Map<String, String> invalidParams = new LinkedHashMap<>();
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            invalidParams.put(error.getObjectName(), getErrorMessage(error));
        }
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            invalidParams.put(error.getField(), getErrorMessage(error));
        }
        body.setProperty("invalid_params", invalidParams);
        body.setStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        return handleExceptionInternal(ex, body, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    //   https://howtodoinjava.com/spring-mvc/spring-problemdetail-errorresponse/#5-adding-problemdetail-to-custom-exceptions
    @ExceptionHandler(AppException.class)
    public ProblemDetail appException(AppException ex, WebRequest request) {
        log.error("ApplicationException: {}", ex.getMessage());
        return createProblemDetail(ex, ex.getStatusCode(), request);
    }

    @ExceptionHandler(DataConflictException.class)
    public ProblemDetail dataConflictException(DataConflictException ex, WebRequest request) {
        log.error("DataConflictException: {}", ex.getMessage());
        return createProblemDetail(ex, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail exception(Exception ex, WebRequest request) {
        String msg = ex.getClass().getName();
        log.error("Exception: " + msg, ex);
        return createProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, msg, request);
    }

    private ProblemDetail createProblemDetail(Exception ex, HttpStatusCode statusCode, WebRequest request) {
        return createProblemDetail(ex, statusCode, ex.getMessage(), request);
    }

    private ProblemDetail createProblemDetail(Exception ex, HttpStatusCode statusCode, @NonNull String msg, WebRequest request) {
        return createProblemDetail(ex, statusCode, msg, null, null, request);
    }

    private String getErrorMessage(ObjectError error) {
        return messageSource.getMessage(
                error.getCode(), error.getArguments(), error.getDefaultMessage(), LocaleContextHolder.getLocale());
    }
}