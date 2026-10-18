package chess.service;

import chess.game.GameSession;
import chess.model.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Сценарии сервиса выполняются без HTTP-сервера, контроллера и HttpExchange. */
class GameServiceTest {
    @Test void exposesConfiguredRetriesOnlyForChatGptWithoutSecrets() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        var settings = new chess.config.OpenAiProperties("test-secret", "test-model", java.time.Duration.ofSeconds(10), 10);
        var service = new GameService(session, "", "", settings);
        var owner = new ClientIdentity(true, null);
        assertEquals(0, state(service, owner).get("opponentRetryLimit"));
        var response = service.execute(owner, command(GameOperation.NEW_GAME, Map.of("mode", "chatgpt")));
        assertEquals(10, ((Map<?, ?>)response.body()).get("opponentRetryLimit"));
        assertEquals(10, state(service, owner).get("opponentRetryLimit"));
        assertFalse(state(service, owner).toString().contains("test-secret"));
    }

    private static ApiRequest command(GameOperation operation, Map<String, Object> data) {
        return new ApiRequest(operation, operation != GameOperation.STATE, data);
    }

    private static ClientIdentity login(GameService service, String code) throws Exception {
        var response = service.execute(new ClientIdentity(false, null), command(GameOperation.LOGIN, Map.of("code", code)));
        String cookies = response.cookies().stream().filter(c -> c.maxAge() > 0)
                .map(c -> c.name() + "=" + c.value()).collect(Collectors.joining("; "));
        return new ClientIdentity(false, cookies);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> state(GameService service, ClientIdentity client) throws Exception {
        return (Map<String, Object>)service.execute(client, command(GameOperation.STATE, Map.of())).body();
    }

    @Test void rolesAndResetWorkWithoutHttp() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        session.newGame("network", 3, "normal");
        var service = new GameService(session, "test-owner-code-123456", "test-friend-code-654321");
        var owner = login(service, "test-owner-code-123456");
        var friend = login(service, "test-friend-code-654321");
        assertEquals(true, state(service, owner).get("playerWhite"));
        assertEquals(false, state(service, friend).get("playerWhite"));
        var version = state(service, owner).get("version");
        var denied = assertThrows(ServiceException.class, () -> service.execute(friend,
                command(GameOperation.EXPORT, Map.of("version", version))));
        assertEquals(ServiceException.Reason.FORBIDDEN, denied.reason());
        service.execute(owner, command(GameOperation.RESET, Map.of("version", version)));
        assertEquals(ServiceException.Reason.UNAUTHORIZED,
                assertThrows(ServiceException.class, () -> state(service, owner)).reason());
        assertEquals(ServiceException.Reason.UNAUTHORIZED,
                assertThrows(ServiceException.class, () -> state(service, friend)).reason());
        var rejoined = login(service, "test-owner-code-123456");
        assertEquals(0, ((Number)state(service, rejoined).get("ply")).intValue());
    }

    @Test void localGuestReceivesCookieAsDataAndOwnView() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        session.newGame("network", 3, "normal");
        var service = new GameService(session, "", "");
        var guest = service.execute(new ClientIdentity(false, null), command(GameOperation.STATE, Map.of()));
        assertEquals(1, guest.cookies().size());
        assertFalse(guest.cookies().get(0).secure());
        assertEquals(false, ((Map<?, ?>)guest.body()).get("playerWhite"));
    }
    @Test void guestCanResignOutOfTurnButCannotChooseWhichSideResigns() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        session.newGame("network", 3, "normal");
        var service = new GameService(session, "test-owner-code-123456", "test-friend-code-654321");
        var owner = login(service, "test-owner-code-123456");
        var friend = login(service, "test-friend-code-654321");
        var version = state(service, friend).get("version");
        var response = service.execute(friend, command(GameOperation.RESIGN,
                Map.of("version", version, "gameId", state(service, friend).get("gameId"), "white", true)));
        assertEquals("blackResigned", ((Map<?, ?>) response.body()).get("result"));
        assertEquals("blackResigned", state(service, owner).get("result"));
        assertEquals(false, ((Map<?, ?>) response.body()).get("playerWhite"));
        assertEquals(java.util.List.of(), state(service, friend).get("actions"));
    }

    @Test void ownerResignsOwnSideDuringGuestTurnAndRequiresAuthentication() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        session.newGame("network", 3, "normal");
        var service = new GameService(session, "test-owner-code-123456", "test-friend-code-654321");
        var owner = login(service, "test-owner-code-123456");
        var initial = state(service, owner);
        var actions = (java.util.List<?>) initial.get("actions");
        var id = ((Map<?, ?>) actions.getFirst()).get("id");
        service.execute(owner, command(GameOperation.PLAY, Map.of("version", initial.get("version"), "actionId", id)));
        var version = state(service, owner).get("version");
        assertEquals(false, state(service, owner).get("whiteTurn"));
        assertThrows(ServiceException.class, () -> service.execute(new ClientIdentity(false, null),
                command(GameOperation.RESIGN, Map.of("version", version, "gameId", state(service, owner).get("gameId")))));
        assertEquals("", state(service, owner).get("result"));
        service.execute(owner, command(GameOperation.RESIGN, Map.of("version", version, "gameId", state(service, owner).get("gameId"))));
        assertEquals("whiteResigned", state(service, owner).get("result"));
    }

    @Test void restartedServerRejectsResignationEvenWhenVersionIsTheSame() throws Exception {
        var client = new ClientIdentity(true, null);
        var before = new GameService(chess.game.TestSessions.create(g -> "draw"), "", "");
        var old = state(before, client);
        var restarted = new GameService(chess.game.TestSessions.create(g -> "draw"), "", "");
        var current = state(restarted, client);
        assertEquals(old.get("version"), current.get("version"));
        assertNotEquals(old.get("gameId"), current.get("gameId"));
        assertThrows(chess.game.GameSession.StalePositionException.class, () -> restarted.execute(client,
                command(GameOperation.RESIGN, Map.of("version", old.get("version"), "gameId", old.get("gameId")))));
        assertEquals(current, state(restarted, client));
        restarted.execute(client, command(GameOperation.RESIGN,
                Map.of("version", current.get("version"), "gameId", current.get("gameId"))));
        assertEquals("whiteResigned", state(restarted, client).get("result"));
    }

    @Test void cloudOnlyAllowsTwoPlayerGamesIncludingImportedSaves() throws Exception {
        var session = chess.game.TestSessions.create(g -> "draw");
        session.newGame("network", 3, "normal");
        var service = new GameService(session, "test-owner-code-123456", "test-friend-code-654321");
        var owner = login(service, "test-owner-code-123456");
        assertEquals(true, state(service, owner).get("networkOnly"));
        var original = state(service, owner);
        for (String mode : java.util.List.of("ai", "chatgpt", "human")) {
            assertThrows(IllegalArgumentException.class, () -> service.execute(owner,
                    command(GameOperation.NEW_GAME, Map.of("mode", mode))));
            assertEquals(original, state(service, owner));
        }
        var save = new java.util.HashMap<>(session.exportGame(((Number) original.get("version")).longValue()));
        save.put("settings", java.util.List.of("ai", 3, "normal"));
        assertThrows(IllegalArgumentException.class, () -> service.execute(owner,
                command(GameOperation.IMPORT, Map.of("version", original.get("version"), "save", save))));
        assertEquals(original, state(service, owner));
        service.execute(owner, command(GameOperation.NEW_GAME, Map.of()));
        assertEquals("network", state(service, owner).get("mode"));
    }

}
