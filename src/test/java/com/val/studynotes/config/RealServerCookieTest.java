package com.val.studynotes.config;

import com.val.studynotes.support.PostgresSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cookie и CSRF на настоящем сервере (встроенный Tomcat на случайном порту) и настоящем HTTP-клиенте, без MockMvc.
 * Сырые заголовки Set-Cookie разбираются вручную: cookie-менеджер клиента отключён, так видно ровно то, что отправил сервер.
 * База та же общая (см. PostgresSpringBootTest), второй контейнер не создаётся.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.username=test-user",
        "app.security.password=test-password"
})
class RealServerCookieTest extends PostgresSpringBootTest {

    private static final String XSRF = "XSRF-TOKEN";

    @LocalServerPort
    private int port;

    @Value("${app.security.username}")
    private String username;

    @Value("${app.security.password}")
    private String password;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String cookieHeader, String xsrfHeader, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        if (xsrfHeader != null) {
            builder.header("X-XSRF-TOKEN", xsrfHeader);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static List<String> setCookies(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie");
    }

    private static List<String> named(List<String> setCookies, String name) {
        return setCookies.stream().filter(h -> h.startsWith(name + "=")).toList();
    }

    private static String valueOf(String setCookie) {
        String pair = setCookie.split(";", 2)[0];
        return pair.substring(pair.indexOf('=') + 1);
    }

    private static boolean hasAttribute(String setCookie, String attribute) {
        return java.util.Arrays.stream(setCookie.split(";")).skip(1)
                .anyMatch(a -> a.trim().equalsIgnoreCase(attribute));
    }

    @Test
    @DisplayName("Настоящий сервер: JSESSIONID с HttpOnly, XSRF-TOKEN без HttpOnly и с SameSite=Lax, последний XSRF-TOKEN действует")
    void realServer_cookiesAfterLogin() throws Exception {
        // 1. Любой запрос к /api/** (здесь 401) выдаёт cookie XSRF-TOKEN
        HttpResponse<String> first = send("GET", "/api/auth/me", null, null, null);
        assertEquals(401, first.statusCode());
        List<String> firstXsrf = named(setCookies(first), XSRF);
        assertEquals(1, firstXsrf.size(), "ожидается одна XSRF-TOKEN: " + setCookies(first));
        assertFalse(hasAttribute(firstXsrf.get(0), "HttpOnly"), "XSRF-TOKEN должна читаться из JS: " + firstXsrf.get(0));
        assertTrue(hasAttribute(firstXsrf.get(0), "SameSite=Lax"), "нет SameSite=Lax: " + firstXsrf.get(0));
        String oldToken = valueOf(firstXsrf.get(0));
        assertFalse(oldToken.isEmpty());

        // 2. Вход с токеном из cookie
        String loginBody = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> login = send("POST", "/api/auth/login", XSRF + "=" + oldToken, oldToken, loginBody);
        assertEquals(200, login.statusCode());
        List<String> loginCookies = setCookies(login);

        List<String> session = named(loginCookies, "JSESSIONID");
        assertEquals(1, session.size(), "ожидается один JSESSIONID: " + loginCookies);
        assertTrue(hasAttribute(session.get(0), "HttpOnly"), "JSESSIONID без HttpOnly: " + session.get(0));

        // В ответе на вход две XSRF-TOKEN: сначала удаляющая (пустое значение), затем новая; применяется последняя
        List<String> loginXsrf = named(loginCookies, XSRF);
        assertEquals(2, loginXsrf.size(), "ожидались удаляющая и новая XSRF-TOKEN: " + loginCookies);
        assertEquals("", valueOf(loginXsrf.get(0)), "первая XSRF-TOKEN должна удалять старую: " + loginXsrf.get(0));
        String newXsrf = loginXsrf.get(1);
        assertFalse(valueOf(newXsrf).isEmpty(), "последняя XSRF-TOKEN пустая");
        assertNotEquals(oldToken, valueOf(newXsrf));
        for (String h : loginXsrf) {
            assertFalse(hasAttribute(h, "HttpOnly"), "XSRF-TOKEN с HttpOnly: " + h);
            assertTrue(hasAttribute(h, "SameSite=Lax"), "нет SameSite=Lax: " + h);
        }
        String newToken = valueOf(newXsrf);
        String sessionId = valueOf(session.get(0));
        String cookieHeader = "JSESSIONID=" + sessionId + "; " + XSRF + "=" + newToken;

        // 3. Сессия работает
        HttpResponse<String> me = send("GET", "/api/auth/me", cookieHeader, null, null);
        assertEquals(200, me.statusCode());
        assertEquals("{\"username\":\"" + username + "\"}", me.body());

        // 4. Старый токен с новой cookie не проходит, новый проходит
        HttpResponse<String> withOld = send("POST", "/api/auth/logout", cookieHeader, oldToken, null);
        assertEquals(403, withOld.statusCode());
        HttpResponse<String> withNew = send("POST", "/api/auth/logout", cookieHeader, newToken, null);
        assertEquals(204, withNew.statusCode());

        // 5. После выхода сессия мертва
        assertEquals(401, send("GET", "/api/auth/me", cookieHeader, null, null).statusCode());
    }
}
