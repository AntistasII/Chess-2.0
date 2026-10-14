package chess.api;

import chess.game.GameSession;
import chess.json.Json;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Two independent browsers, tested at the HTTP boundary without opening sockets. */
public final class NetworkApiTest {
    private static final class Exchange extends HttpExchange {
        final Headers request = new Headers(), response = new Headers();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final String path, method;
        final boolean host;
        InputStream input;
        int status;
        Exchange(String path, boolean host, Map<String, Object> body, String cookie) {
            this.path = path; this.host = host; method = body == null ? "GET" : "POST";
            input = new ByteArrayInputStream(body == null ? new byte[0] : Json.stringify(body).getBytes(StandardCharsets.UTF_8));
            request.set("Host", host ? "localhost:8080" : "192.168.1.2:8080");
            request.set("Origin", "http://" + request.getFirst("Host"));
            request.set("Content-Type", "application/json");
            if (cookie != null) request.set("Cookie", cookie);
        }
        Map<String, Object> json() { return Json.object(output.toString(StandardCharsets.UTF_8)); }
        public Headers getRequestHeaders() { return request; }
        public Headers getResponseHeaders() { return response; }
        public URI getRequestURI() { return URI.create(path); }
        public String getRequestMethod() { return method; }
        public HttpContext getHttpContext() { return null; }
        public void close() { }
        public InputStream getRequestBody() { return input; }
        public OutputStream getResponseBody() { return output; }
        public void sendResponseHeaders(int status, long length) { this.status = status; }
        public InetSocketAddress getRemoteAddress() { return new InetSocketAddress(host ? "127.0.0.1" : "192.168.1.3", 50000); }
        public int getResponseCode() { return status; }
        public InetSocketAddress getLocalAddress() { return new InetSocketAddress("192.168.1.2", 8080); }
        public String getProtocol() { return "HTTP/1.1"; }
        public Object getAttribute(String name) { return null; }
        public void setAttribute(String name, Object value) { }
        public void setStreams(InputStream input, OutputStream output) { throw new UnsupportedOperationException(); }
        public HttpPrincipal getPrincipal() { return null; }
    }
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Exchange call(ApiHandler api, String path, boolean host, Map<String, Object> body, String cookie) throws IOException {
        Exchange exchange = new Exchange(path, host, body, cookie); api.handle(exchange); return exchange;
    }
    public static void main(String[] args) throws Exception {
        var session = new GameSession(g -> { throw new AssertionError("Network must not invoke AI"); });
        var api = new ApiHandler(session);
        check(call(api, "/api/state", false, null, null).status == 403, "Guest cannot read local games");
        var start = call(api, "/api/new", true, Map.of("mode", "network"), null);
        check(start.status == 200 && start.json().get("playerWhite").equals(true), "Host owns white");
        long version = ((Number) start.json().get("version")).longValue();
        var join = call(api, "/api/state", false, null, null);
        String cookie = join.response.getFirst("Set-Cookie").split(";")[0];
        check(join.status == 200 && join.json().get("playerWhite").equals(false), "First guest owns black");
        check(((List<?>) join.json().get("actions")).isEmpty(), "Black cannot see white actions");
        check(call(api, "/api/action", false, Map.of("version", version, "actionId", "draw"), cookie).status == 400, "Wrong-side command rejected");
        var whiteDraw = call(api, "/api/action", true, Map.of("version", version, "actionId", "draw"), null);
        version = ((Number) whiteDraw.json().get("version")).longValue();
        var blackView = call(api, "/api/state", false, null, cookie);
        check(((List<?>) blackView.json().get("hand")).isEmpty() && ((Number)blackView.json().get("enemyCards")).intValue() == 1, "White cards remain hidden");
        check(call(api, "/api/action", true, Map.of("version", version, "actionId", "draw"), null).status == 400, "Host cannot move black");
        var blackDraw = call(api, "/api/action", false, Map.of("version", version, "actionId", "draw"), cookie);
        check(blackDraw.status == 200 && blackDraw.json().get("handWhite").equals(false), "Black command returns black hand");
        var hostView = call(api, "/api/state", true, null, null).json();
        var whiteCard = ((Map<?, ?>)((List<?>)hostView.get("hand")).get(0)).get("id");
        var blackCard = ((Map<?, ?>)((List<?>)blackDraw.json().get("hand")).get(0)).get("id");
        check(!whiteCard.equals(blackCard), "Each browser receives only its own cards");
        check(call(api, "/api/state", false, null, cookie).status == 200, "Guest reconnects using cookie");
        check(call(api, "/api/state", false, null, null).status == 403, "Third browser cannot claim black");
        version = ((Number)hostView.get("version")).longValue();
        for (String path : List.of("new", "save", "load", "export", "import", "ai", "reset"))
            check(call(api, "/api/" + path, false, Map.of("version", version), cookie).status == 403, "Guest admin denied: " + path);
        check(call(api, "/api/ai", true, Map.of("version", version), null).status == 400, "Network rejects AI commands");
        var foreign = new Exchange("/api/action", false, Map.of("version", version, "actionId", "draw"), cookie);
        foreign.request.set("Origin", "http://unrelated.example"); api.handle(foreign);
        check(foreign.status == 403, "Foreign-origin commands rejected");
        var cloudSession = new GameSession(g -> "draw");
        cloudSession.newGame("network", 3, "normal");
        var cloud = new ApiHandler(cloudSession, "test-owner-code-123456", "test-friend-code-654321");
        check(call(cloud, "/api/state", true, null, null).status == 401, "Proxy loopback cannot bypass cloud login");
        check(call(cloud, "/api/login", false, Map.of("code", "wrong"), null).status == 401, "Wrong code rejected");
        var login = call(cloud, "/api/login", false, Map.of("code", "test-owner-code-123456"), null);
        String ownerCookie = login.response.get("Set-Cookie").stream().filter(c -> c.startsWith("chess_host="))
                .findFirst().orElseThrow().split(";")[0];
        check(login.status == 200 && login.response.getFirst("Set-Cookie").contains("Secure"), "Owner receives secure cookie");
        check(call(cloud, "/api/state", false, null, ownerCookie).json().get("playerWhite").equals(true), "Remote owner gets white");
        login = call(cloud, "/api/login", false, Map.of("code", "test-friend-code-654321"), null);
        String friendCookie = login.response.get("Set-Cookie").stream().filter(c -> c.startsWith("chess_guest="))
                .findFirst().orElseThrow().split(";")[0];
        check(call(cloud, "/api/state", false, null, friendCookie).json().get("playerWhite").equals(false), "Invitation gets black");
        check(call(cloud, "/api/new", false, Map.of("mode", "network"), friendCookie).status == 403, "Friend cannot reset cloud game");
        var secureRequest = new Exchange("/api/new", false, Map.of("mode", "network"), ownerCookie);
        secureRequest.request.set("Origin", "https://192.168.1.2:8080"); cloud.handle(secureRequest);
        check(secureRequest.status == 200, "HTTPS origin accepted behind proxy");
        long cloudVersion = ((Number)cloudSession.state().get("version")).longValue();
        call(cloud, "/api/action", false, Map.of("version", cloudVersion, "actionId", "draw"), ownerCookie);
        cloudVersion++;
        check(call(cloud, "/api/reset", false, Map.of("version", cloudVersion), friendCookie).status == 403, "Friend cannot reset sessions");
        check(call(cloud, "/api/reset", false, Map.of("version", cloudVersion - 1), ownerCookie).status == 409, "Stale reset rejected");
        check(call(cloud, "/api/reset", false, Map.of("version", cloudVersion), ownerCookie).status == 200, "Owner can reset completely");
        check(call(cloud, "/api/state", false, null, ownerCookie).status == 401, "Old owner cookie invalidated");
        check(call(cloud, "/api/state", false, null, friendCookie).status == 401, "Old friend cookie invalidated");
        check(((Number)cloudSession.state().get("ply")).intValue() == 0
                && ((List<?>)cloudSession.state().get("hand")).isEmpty(), "Reset clears moves and cards");
        check(call(cloud, "/api/login", false, Map.of("code", "test-owner-code-123456"), null).status == 200, "Same owner code works after reset");
        check(call(cloud, "/api/login", false, Map.of("code", "test-friend-code-654321"), null).status == 200, "Same invitation works after reset");
        System.out.println("Passed " + checks + " network HTTP checks: seats, turns, hands, cookies and permissions.");
    }
}
