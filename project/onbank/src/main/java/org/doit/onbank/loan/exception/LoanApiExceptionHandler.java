package org.doit.onbank.loan.exception;

import java.util.Map;
import java.util.NoSuchElementException;

import org.doit.onbank.loan.controller.LoanApiController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LoanApiController.class)
public class LoanApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> badFields(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "신청 항목을 확인해주세요."));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String,String>> notFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }
}