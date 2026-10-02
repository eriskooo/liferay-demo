package com.example.tasks.common;

import com.example.tasks.task.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centrální mapování výjimek na RFC 9457 ProblemDetail.
 * V portletu se chyby předávaly přes SessionErrors + &lt;liferay-ui:error&gt; v JSP.
 * Validační chyby (@Valid -> 400) řeší zděděný ResponseEntityExceptionHandler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/** Neexistující úkol -> 404 */
	@ExceptionHandler(TaskNotFoundException.class)
	public ProblemDetail handleNotFound(TaskNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Task not found");
		return problem;
	}

}
