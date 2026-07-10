package org.github.nynosy.adiresy_mobile.data;

public final class Result<T> {

    public enum Status { SUCCESS, STALE, ERROR_HTTP, ERROR_NETWORK }

    public final Status status;
    public final T data;
    public final int httpCode;
    public final String message;
    public final Throwable error;

    private Result(Status status, T data, int httpCode, String message, Throwable error) {
        this.status   = status;
        this.data     = data;
        this.httpCode = httpCode;
        this.message  = message;
        this.error    = error;
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(Status.SUCCESS, data, 0, null, null);
    }

    /** Data was served from a stale cache (network unavailable or returned error). */
    public static <T> Result<T> stale(T data) {
        return new Result<>(Status.STALE, data, 0, null, null);
    }

    public static <T> Result<T> error(int httpCode, String message) {
        return new Result<>(Status.ERROR_HTTP, null, httpCode, message, null);
    }

    public static <T> Result<T> networkError(Throwable error) {
        return new Result<>(Status.ERROR_NETWORK, null, 0, null, error);
    }

    public boolean isSuccess()  { return status == Status.SUCCESS; }
    public boolean isStale()    { return status == Status.STALE;   }
    public boolean isError()    { return status == Status.ERROR_HTTP || status == Status.ERROR_NETWORK; }

    public boolean isApiKeyError() {
        return status == Status.ERROR_HTTP && httpCode == 401;
    }
}
