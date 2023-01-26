package ru.javaops.topjava.web;

import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import ru.javaops.topjava.error.DataConflictException;
import ru.javaops.topjava.error.IllegalRequestDataException;
import ru.javaops.topjava.error.NotFoundException;
import ru.javaops.topjava.util.validation.ValidationUtil;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@AllArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private final MessageSource messageSource;

    private static final Map<Class<?>, HttpStatus> HTTP_STATUS_MAP = Map.of(
            EntityNotFoundException.class, HttpStatus.UNPROCESSABLE_ENTITY,
            DataIntegrityViolationException.class, HttpStatus.UNPROCESSABLE_ENTITY,
            IllegalRequestDataException.class, HttpStatus.UNPROCESSABLE_ENTITY,
            NotFoundException.class, HttpStatus.NOT_FOUND,
            DataConflictException.class, HttpStatus.CONFLICT
    );

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
    @ExceptionHandler(Exception.class)
    public ProblemDetail exception(Exception ex, WebRequest request) {
        HttpStatus status = HTTP_STATUS_MAP.get(ex.getClass());
        if (status != null) {
            log.error("Exception: {}", ex.toString());
            return createProblemDetail(ex, status, request);
        } else {
            Throwable root = ValidationUtil.getRootCause(ex);
            log.error("Exception: " + root, root);
            return createProblemDetail(ex, HttpStatus.INTERNAL_SERVER_ERROR, root.getClass().getName(), request);
        }
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