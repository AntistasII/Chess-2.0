package chess.service;

import chess.model.*;

import java.util.ArrayList;
import java.util.List;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * Вход, роли и cookies игроков. В облаке роль определяется кодом, а не адресом прокси.
 * Изменения токенов выполняются сервисом GameService под монитором GameSession.
 */
public final class PlayerAccessService {
    private static final String OWNER_COOKIE = "chess_host";
    private static final String GUEST_COOKIE = "chess_guest";
    private static final Set<GameOperation> GUEST_OPERATIONS = Set.of(GameOperation.STATE, GameOperation.PLAY, GameOperation.SCOUT, GameOperation.DRAW);
    private static final String OWNER_ONLY = "Команда доступна только владельцу сервера";

    private final String hostKey;
    private final String guestKey;
    // Проверка владельца перед долгим запросом к ИИ возможна без монитора сессии.
    private volatile String hostToken = newToken();
    private String guestToken;

    public PlayerAccessService(String hostKey, String guestKey) {
        this.hostKey = hostKey;
        this.guestKey = guestKey;
    }

    public boolean isLogin(ApiRequest request) {
        return cloudMode() && request.isPost() && request.operation() == GameOperation.LOGIN;
    }

    /**
     * Код не сохраняется в браузере: вместо него выдаётся случайный HttpOnly-токен.
     */
    public List<CookieChange> login(ApiRequest request) {
        var cookies = new ArrayList<CookieChange>();
        String code = request.input().get("code") instanceof String value ? value : "";
        if (matches(code, hostKey)) {
            setCookie(cookies, OWNER_COOKIE, hostToken);
            clearCookie(cookies, GUEST_COOKIE);
        } else if (matches(code, guestKey)) {
            if (guestToken == null) guestToken = newToken();
            setCookie(cookies, GUEST_COOKIE, guestToken);
            clearCookie(cookies, OWNER_COOKIE);
        } else throw new ServiceException(ServiceException.Reason.UNAUTHORIZED, "Неверный код входа");
        return cookies;
    }

    /**
     * Возвращает признак владельца. В сетевой партии владелец играет белыми, гость — чёрными.
     */
    public AccessGrant authorize(ClientIdentity client, ApiRequest request, boolean networkGame) {
        if (isOwner(client)) return new AccessGrant(true, List.of());
        if (!networkGame)
            throw new ServiceException(ServiceException.Reason.FORBIDDEN, "Владелец должен начать сетевую партию");
        var cookies = new ArrayList<CookieChange>();
        requireGuest(client, request, cookies);
        if (!GUEST_OPERATIONS.contains(request.operation()))
            throw new ServiceException(ServiceException.Reason.FORBIDDEN, OWNER_ONLY);
        return new AccessGrant(false, cookies);
    }

    public void requireOwner(ClientIdentity client) {
        if (!isOwner(client)) throw new ServiceException(ServiceException.Reason.FORBIDDEN, OWNER_ONLY);
    }

    private boolean isOwner(ClientIdentity client) {
        return cloudMode() ? hasCookie(client, OWNER_COOKIE, hostToken)
                : client.loopback();
    }

    private void requireGuest(ClientIdentity client, ApiRequest request, List<CookieChange> cookies) {
        if (hasCookie(client, GUEST_COOKIE, guestToken)) return;
        if (cloudMode())
            throw new ServiceException(ServiceException.Reason.UNAUTHORIZED, "Введите код владельца или код приглашения");
        // В локальной сети первый удалённый браузер занимает чёрных без ввода кода.
        if (guestToken == null && request.operation() == GameOperation.STATE && !request.isPost()) {
            guestToken = newToken();
            setCookie(cookies, GUEST_COOKIE, guestToken);
            return;
        }
        throw new ServiceException(ServiceException.Reason.FORBIDDEN, "Место чёрных уже занято другим браузером");
    }

    /**
     * Старые cookies перестают работать у обоих игроков, но коды приглашения сохраняются.
     */
    public List<CookieChange> reset() {
        hostToken = newToken();
        guestToken = null;
        var cookies = new ArrayList<CookieChange>();
        clearCookie(cookies, OWNER_COOKIE);
        clearCookie(cookies, GUEST_COOKIE);
        return cookies;
    }

    private boolean cloudMode() {
        return !hostKey.isBlank();
    }

    private static String newToken() {
        return UUID.randomUUID().toString();
    }

    private static boolean matches(String input, String expected) {
        return !expected.isBlank() && MessageDigest.isEqual(
                input.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean hasCookie(ClientIdentity client, String name, String token) {
        String cookie = client.cookie();
        return cookie != null && token != null && Arrays.stream(cookie.split(";"))
                .anyMatch(part -> part.trim().equals(name + "=" + token));
    }

    private void setCookie(List<CookieChange> cookies, String name, String token) {
        cookies.add(new CookieChange(name, token, 2592000, cloudMode()));
    }

    private void clearCookie(List<CookieChange> cookies, String name) {
        cookies.add(new CookieChange(name, "", 0, cloudMode()));
    }
}
