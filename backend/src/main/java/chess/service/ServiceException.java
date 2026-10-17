package chess.service;

/** Отказ прикладного сценария; в HTTP-статус его переводит контроллер. */
public final class ServiceException extends RuntimeException {
    public enum Reason { UNAUTHORIZED, FORBIDDEN, NOT_FOUND }
    private final Reason reason;

    public ServiceException(Reason reason, String message) { super(message); this.reason = reason; }
    public Reason reason() { return reason; }
}
