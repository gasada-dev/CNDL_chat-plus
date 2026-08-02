package ru.gasada.chatresponder;

public record TemplateOperationResult<T>(boolean success, T value, String errorMessage, Throwable error) {
	public static <T> TemplateOperationResult<T> success(T value) {
		return new TemplateOperationResult<>(true, value, "", null);
	}

	public static <T> TemplateOperationResult<T> failure(String errorMessage, Throwable error) {
		return new TemplateOperationResult<>(false, null, errorMessage, error);
	}
}
