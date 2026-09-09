package com.goatteen.trading.common.api;

import com.goatteen.trading.auth.exception.DuplicateEmailException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.goatteen.trading.auth.exception.InvalidCredentialsException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(DuplicateEmailException.class)
        public ResponseEntity<ApiError> handleDuplicateEmail(
                        DuplicateEmailException exception,
                        HttpServletRequest request) {

                ApiError error = new ApiError(
                                HttpStatus.CONFLICT.value(),
                                "Conflict",
                                exception.getMessage(),
                                request.getRequestURI(),
                                null);

                return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(
                        MethodArgumentNotValidException exception,
                        HttpServletRequest request) {

                Map<String, String> fieldErrors = new LinkedHashMap<>();

                exception.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> fieldErrors.putIfAbsent(
                                                error.getField(),
                                                error.getDefaultMessage()));

                ApiError error = new ApiError(
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                "Validation failed",
                                request.getRequestURI(),
                                fieldErrors);

                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(InvalidCredentialsException.class)
        public ResponseEntity<ApiError> handleInvalidCredentials(
                        InvalidCredentialsException exception,
                        HttpServletRequest request) {

                ApiError error = new ApiError(
                                HttpStatus.UNAUTHORIZED.value(),
                                "Unauthorized",
                                exception.getMessage(),
                                request.getRequestURI(),
                                null);

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(error);
        }
}