package ru.gasada.cndlchatplus;

record ConfigOperationResult<T>(boolean success, T value, String errorMessage, Throwable error) {
	static <T> ConfigOperationResult<T> success(T value) {
		return new ConfigOperationResult<>(true, value, "", null);
	}

	static <T> ConfigOperationResult<T> failure(String errorMessage, Throwable error) {
		return new ConfigOperationResult<>(false, null, errorMessage, error);
	}
}
