package chess.api;

/** Ожидаемый отказ HTTP: статус и безопасное сообщение без внутренних деталей сервера. */
public final class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
