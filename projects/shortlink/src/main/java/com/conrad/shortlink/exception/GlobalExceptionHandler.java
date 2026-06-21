package com.conrad.shortlink.exception;

import com.conrad.shortlink.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 教学点：
 * 1. @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *    - 自动应用到所有 @RestController，把异常转换为 JSON 响应
 * 2. @ExceptionHandler(ExceptionType.class) 声明处理哪种异常
 * 3. 统一在这里处理，业务代码只需 throw，不用 try-catch
 *
 * 对应学习模块：notes/java/08-spring (AOP 统一异常处理)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ShortLinkNotFoundException ex,
                                                         HttpServletRequest request) {
        ErrorResponse err = new ErrorResponse(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex,
                                                          HttpServletRequest request) {
        ErrorResponse err = new ErrorResponse(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Too Many Requests",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(err);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex,
                                                           HttpServletRequest request) {
        ErrorResponse err = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    /**
     * 参数校验失败：@Valid 触发
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<String> details = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.toList());

        ErrorResponse err = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Failed",
            "请求参数校验失败",
            request.getRequestURI()
        );
        err.setDetails(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }

    /**
     * 兜底：处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        ErrorResponse err = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            ex.getMessage() != null ? ex.getMessage() : "未知错误",
            request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }
}
