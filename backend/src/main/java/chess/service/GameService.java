package chess.service;

import chess.game.GameSession;
import chess.config.OpenAiProperties;
import chess.model.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Прикладные сценарии: доступ, очередь, версия, команда и безопасный снимок результата.
 */
public final class GameService {
    private static final Set<GameOperation> COMMANDS = Set.of(GameOperation.PLAY, GameOperation.AI,
            GameOperation.SCOUT, GameOperation.DRAW, GameOperation.SAVE, GameOperation.LOAD,
            GameOperation.EXPORT, GameOperation.IMPORT);
    private static final Set<GameOperation> PLAYER_COMMANDS = Set.of(GameOperation.PLAY, GameOperation.SCOUT, GameOperation.DRAW);
    private static final Map<String, Object> OK = Map.of("ok", true);
    private final GameSession session;
    private final PlayerAccessService access;
    private final OpenAiProperties openAi;

    public GameService(GameSession session, String hostKey, String guestKey) {
        this(session, hostKey, guestKey, null);
    }

    public GameService(GameSession session, String hostKey, String guestKey, OpenAiProperties openAi) {
        this.session = session;
        this.openAi = openAi;
        access = new PlayerAccessService(hostKey, guestKey);
    }

    public ApiResponse execute(ClientIdentity client, ApiRequest request) throws IOException {
        if (request.isAi()) {
            access.requireOwner(client);
            // GameSession отпускает монитор на время расчёта ИИ и проверяет версию перед записью.
            return route(client, request);
        }
        // Определение роли, изменение партии и снимок должны относиться к одному состоянию.
        synchronized (session) {
            return route(client, request);
        }
    }

    private ApiResponse route(ClientIdentity client, ApiRequest request) throws IOException {
        if (access.isLogin(request)) return new ApiResponse(OK, access.login(request));
        AccessGrant grant = access.authorize(client, request, session.isNetwork());
        boolean owner = grant.owner();
        if (request.operation() == GameOperation.STATE && !request.isPost())
            return new ApiResponse(view(owner), grant.cookies());
        if (!request.isPost()) throw notFound();

        if (request.operation() == GameOperation.NEW_GAME) {
            session.newGame(request.text("mode", null), request.optionalInteger("handLimit"), request.text("difficulty", null));
            return new ApiResponse(view(owner), grant.cookies());
        }
        if (request.operation() == GameOperation.RESET) {
            session.requireVersion(request.version());
            session.reset();
            return new ApiResponse(OK, access.reset());
        }
        return command(request, grant);
    }

    private ApiResponse command(ApiRequest request, AccessGrant grant) throws IOException {
        var operation = request.operation();
        if (!COMMANDS.contains(operation)) throw notFound();
        long version = request.version();
        // Устаревшая команда отклоняется до чтения её аргументов, как и раньше.
        session.requireVersion(version);
        if (session.isNetwork() && PLAYER_COMMANDS.contains(operation)) session.requirePlayerTurn(grant.owner());
        if (operation == GameOperation.EXPORT)
            return new ApiResponse(session.exportGame(version), grant.cookies(), true);

        Map<String, Object> state = switch (operation) {
            case IMPORT -> session.importGame(version, request.save());
            case SAVE -> session.save(version);
            case LOAD -> session.load(version);
            case AI -> session.runAi(version);
            case SCOUT -> session.scout(version, request.cardId());
            case DRAW -> session.claimDraw(version);
            case PLAY -> session.play(version, request.text("actionId", null));
            default -> throw notFound();
        };
        // Сторона браузера фиксирована: игрок не должен получить чужую руку после своего хода.
        return new ApiResponse(withRequestSettings(session.isNetwork() ? session.playerState(grant.owner()) : state), grant.cookies());
    }

    private Map<String, Object> view(boolean owner) {
        return withRequestSettings(session.isNetwork() ? session.playerState(owner) : session.state());
    }

    private Map<String, Object> withRequestSettings(Map<String, Object> state) {
        // Клиент получает только политику повторов, без ключа и остальных секретов.
        state.put("opponentRetryLimit", openAi != null && "chatgpt".equals(state.get("mode")) ? openAi.maxRetries() : 0);
        return state;
    }

    private static ServiceException notFound() {
        return new ServiceException(ServiceException.Reason.NOT_FOUND, "Неизвестный запрос");
    }
}
