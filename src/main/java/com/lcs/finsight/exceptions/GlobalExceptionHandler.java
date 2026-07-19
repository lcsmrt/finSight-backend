package com.lcs.finsight.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException exception,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    ) {
        List<FieldErrorDto> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDto(error.getField(), error.getDefaultMessage()))
                .toList();
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                "Validation error.",
                ((ServletWebRequest) request).getRequest().getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentials(
            BadCredentialsException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                "Invalid username or password.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    @ExceptionHandler(FinancialTransactionExceptions.FinancialTransactionNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleFinancialTransactionNotFound(
            FinancialTransactionExceptions.FinancialTransactionNotFoundException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleFinancialTransactionSeriesNotFound(
            FinancialTransactionExceptions.FinancialTransactionSeriesNotFoundException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(FinancialTransactionCategoryExceptions.FinancialTransactionCategoryNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleFinancialTransactionCategoryNotFound(
            FinancialTransactionCategoryExceptions.FinancialTransactionCategoryNotFoundException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler({UserExceptions.UserNotFoundException.class, UserExceptions.UsernameNotFoundException.class})
    public ResponseEntity<ErrorResponseDto> handleUserNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(UserExceptions.EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleEmailAlreadyUsed(
            UserExceptions.EmailAlreadyExistsException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler({
            PlanExceptions.PlanNotFoundException.class,
            PlanExceptions.NotAMemberException.class,
            PlanExceptions.MemberNotFoundException.class,
            PlanExceptions.InvitationNotFoundException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePlanNotFound(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler({
            PlanExceptions.InsufficientPlanRoleException.class,
            PlanExceptions.CannotModifyOthersTransactionException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePlanForbidden(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    @ExceptionHandler({
            PlanExceptions.LastPlanException.class,
            PlanExceptions.LastOwnerException.class
    })
    public ResponseEntity<ErrorResponseDto> handlePlanConflict(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(PlanExceptions.InvitationEmailMismatchException.class)
    public ResponseEntity<ErrorResponseDto> handleInvitationEmailMismatch(
            PlanExceptions.InvitationEmailMismatchException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({
            PlanExceptions.InvitationExpiredException.class,
            PlanExceptions.InvitationRevokedException.class,
            PlanExceptions.InvitationAlreadyUsedException.class
    })
    public ResponseEntity<ErrorResponseDto> handleInvitationGone(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.GONE).body(errorResponse);
    }

    @ExceptionHandler({
            FinancialTransactionExceptions.CategoryTypeMismatchException.class,
            FinancialTransactionExceptions.ItemCategoryTypeMismatchException.class,
            FinancialTransactionExceptions.ItemsTotalExceedsAmountException.class,
            FinancialTransactionExceptions.ParticipantSharesMismatchException.class
    })
    public ResponseEntity<ErrorResponseDto> handleFinancialTransactionInvariant(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                exception.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        logger.error("Unexpected error: ", exception);
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                "An internal error occurred. Please try again later.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
