package com.aiopen.platform.modules.auth.github.impl;

import com.aiopen.platform.common.exception.BusinessException;
import com.aiopen.platform.common.result.ResultCode;
import com.aiopen.platform.config.OutboundHttpClientFactory;
import com.aiopen.platform.modules.activitylog.entity.UserActivityLog;
import com.aiopen.platform.modules.activitylog.service.UserActivityLogService;
import com.aiopen.platform.modules.auth.github.GithubOAuthService;
import com.aiopen.platform.modules.auth.github.GithubOAuthSessionStore;
import com.aiopen.platform.modules.setting.SettingKeys;
import com.aiopen.platform.modules.setting.service.SystemSettingService;
import com.aiopen.platform.modules.user.dto.GithubAuthorizeResponse;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.JwtUtil;
import com.aiopen.platform.security.Roles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthServiceImpl implements GithubOAuthService {

    private static final String AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final String EMAILS_URL = "https://api.github.com/user/emails";
    private static final String SCOPE = "read:user user:email";
    private static final int USERNAME_MAX_LENGTH = 50;

    private final SystemSettingService settingService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final GithubOAuthSessionStore sessionStore;
    private final OutboundHttpClientFactory httpClientFactory;
    private final UserActivityLogService activityLogService;

    @Override
    public GithubAuthorizeResponse buildAuthorizeUrl(String redirect, HttpServletRequest request) {
        GithubOAuthConfig config = oauthConfig(request);
        ensureEnabledAndConfigured(config);

        String state = sessionStore.createState(sanitizeRedirect(redirect));
        String url = AUTHORIZE_URL
                + "?client_id=" + enc(config.clientId())
                + "&redirect_uri=" + enc(config.redirectUri())
                + "&scope=" + enc(SCOPE)
                + "&state=" + enc(state);
        return new GithubAuthorizeResponse(url);
    }

    @Override
    public String handleCallback(String code, String state, String error, String errorDescription,
            HttpServletRequest request) {
        GithubOAuthConfig config = oauthConfig(request);
        String redirect = sanitizeRedirect(null);
        if (!StringUtils.hasText(state)) {
            return frontendRedirect(config.frontendCallbackUri(), null, redirect, "invalid_state");
        }
        String stateRedirect = sessionStore.consumeState(state);
        if (!StringUtils.hasText(stateRedirect)) {
            return frontendRedirect(config.frontendCallbackUri(), null, redirect, "invalid_state");
        }
        redirect = sanitizeRedirect(stateRedirect);

        if (StringUtils.hasText(error)) {
            String message = StringUtils.hasText(errorDescription) ? errorDescription : error;
            return frontendRedirect(config.frontendCallbackUri(), null, redirect, message);
        }
        if (!StringUtils.hasText(code)) {
            return frontendRedirect(config.frontendCallbackUri(), null, redirect, "missing_code");
        }

        try {
            ensureEnabledAndConfigured(config);
            String accessToken = exchangeCodeForToken(code, config);
            GithubProfile profile = fetchProfile(accessToken, config);
            LoginResponse loginResponse = loginOrRegister(profile);
            String ticket = sessionStore.createTicket(loginResponse);
            return frontendRedirect(config.frontendCallbackUri(), ticket, redirect, null);
        } catch (BusinessException e) {
            log.warn("GitHub OAuth business failure: code={}, msg={}", e.getCode(), e.getMessage());
            return frontendRedirect(config.frontendCallbackUri(), null, redirect, e.getMessage());
        } catch (HttpTimeoutException e) {
            log.warn("GitHub OAuth network timeout", e);
            return frontendRedirect(config.frontendCallbackUri(), null, redirect,
                    "GitHub OAuth connection timed out. Check backend network or proxy.");
        } catch (IOException e) {
            log.warn("GitHub OAuth network request failed", e);
            return frontendRedirect(config.frontendCallbackUri(), null, redirect,
                    "GitHub OAuth network request failed. Check backend network or proxy.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("GitHub OAuth callback interrupted", e);
            return frontendRedirect(config.frontendCallbackUri(), null, redirect,
                    ResultCode.GITHUB_OAUTH_FAILED.getMessage());
        } catch (Exception e) {
            log.warn("GitHub OAuth callback failed", e);
            return frontendRedirect(config.frontendCallbackUri(), null, redirect,
                    ResultCode.GITHUB_OAUTH_FAILED.getMessage());
        }
    }

    @Override
    public LoginResponse exchangeTicket(String ticket) {
        LoginResponse response = sessionStore.consumeTicket(ticket);
        if (response == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return response;
    }

    protected LoginResponse loginOrRegister(GithubProfile profile) {
        User user = userService.getByGithubId(profile.id());
        boolean isNewUser = false;
        if (user == null) {
            if (!settingService.isRegisterEnabled()) {
                throw new BusinessException(ResultCode.REGISTER_DISABLED);
            }
            user = createGithubUser(profile);
            isNewUser = true;
        } else {
            user = updateGithubProfile(user, profile);
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        recordActivity(user.getId(), user.getUsername(),
                isNewUser ? "REGISTER_GITHUB" : "LOGIN", "USER",
                user.getId(), user.getUsername(),
                isNewUser ? "GitHub OAuth 注册" : "GitHub OAuth 登录", 1);
        return new LoginResponse(token, user);
    }

    private User createGithubUser(GithubProfile profile) {
        User user = new User();
        user.setUsername(uniqueUsername(profile));
        user.setPassword(passwordEncoder.encode("github:" + UUID.randomUUID()));
        user.setEmail(profile.email());
        user.setGithubId(profile.id());
        user.setGithubLogin(profile.login());
        user.setAvatarUrl(truncate(profile.avatarUrl(), 500));
        user.setRole(Roles.USER);
        user.setStatus(1);
        user.setMustChangePassword(0);
        userService.save(user);
        return userService.getById(user.getId());
    }

    private User updateGithubProfile(User user, GithubProfile profile) {
        User update = new User();
        update.setId(user.getId());
        boolean changed = false;

        if (!Objects.equals(user.getGithubLogin(), profile.login())) {
            update.setGithubLogin(profile.login());
            changed = true;
        }
        String avatarUrl = truncate(profile.avatarUrl(), 500);
        if (!Objects.equals(user.getAvatarUrl(), avatarUrl)) {
            update.setAvatarUrl(avatarUrl);
            changed = true;
        }
        if (!StringUtils.hasText(user.getEmail()) && StringUtils.hasText(profile.email())) {
            update.setEmail(profile.email());
            changed = true;
        }

        if (!changed) {
            return user;
        }
        userService.updateById(update);
        return userService.getById(user.getId());
    }

    private String exchangeCodeForToken(String code, GithubOAuthConfig config) throws IOException, InterruptedException {
        String body = "client_id=" + enc(config.clientId())
                + "&client_secret=" + enc(config.clientSecret())
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(config.redirectUri());
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "ai-open-platform")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("GitHub token exchange failed: status={}, body={}", response.statusCode(), response.body());
            throw new BusinessException(ResultCode.GITHUB_OAUTH_FAILED, githubErrorMessage(response.body()));
        }
        JsonNode json = objectMapper.readTree(response.body());
        String accessToken = text(json, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            log.warn("GitHub token exchange did not return access_token: body={}", response.body());
            throw new BusinessException(ResultCode.GITHUB_OAUTH_FAILED, githubErrorMessage(response.body()));
        }
        return accessToken;
    }

    private GithubProfile fetchProfile(String accessToken, GithubOAuthConfig config) throws IOException, InterruptedException {
        JsonNode userJson = getGithubJson(USER_URL, accessToken, config);
        long id = userJson.path("id").asLong(0);
        String login = text(userJson, "login");
        if (id <= 0 || !StringUtils.hasText(login)) {
            throw new BusinessException(ResultCode.GITHUB_OAUTH_FAILED);
        }
        String email = text(userJson, "email");
        if (!StringUtils.hasText(email)) {
            try {
                email = fetchPrimaryEmail(accessToken, config);
            } catch (Exception e) {
                log.warn("GitHub primary email lookup failed, continuing without email: {}", e.getMessage());
            }
        }
        return new GithubProfile(id, login, email, text(userJson, "avatar_url"));
    }

    private String fetchPrimaryEmail(String accessToken, GithubOAuthConfig config) throws IOException, InterruptedException {
        JsonNode emailsJson = getGithubJson(EMAILS_URL, accessToken, config);
        if (!emailsJson.isArray()) {
            return null;
        }
        String firstVerified = null;
        for (JsonNode emailJson : emailsJson) {
            String email = text(emailJson, "email");
            if (!StringUtils.hasText(email) || !emailJson.path("verified").asBoolean(false)) {
                continue;
            }
            if (emailJson.path("primary").asBoolean(false)) {
                return email;
            }
            if (firstVerified == null) {
                firstVerified = email;
            }
        }
        return firstVerified;
    }

    private JsonNode getGithubJson(String url, String accessToken, GithubOAuthConfig config) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", "ai-open-platform")
                .GET()
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("GitHub API request failed: url={}, status={}, body={}", url, response.statusCode(), response.body());
            throw new BusinessException(ResultCode.GITHUB_OAUTH_FAILED,
                    "GitHub API request failed, status " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }

    private void recordActivity(Long userId, String username, String action, String resourceType,
                                Long resourceId, String resourceName, String detail, int status) {
        UserActivityLog log = new UserActivityLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setResourceName(resourceName);
        log.setDetail(detail);
        log.setStatus(status);
        activityLogService.record(log);
    }

    private void ensureEnabledAndConfigured(GithubOAuthConfig config) {
        if (!settingService.isGithubRegisterEnabled()) {
            throw new BusinessException(ResultCode.REGISTER_DISABLED);
        }
        if (!StringUtils.hasText(config.clientId()) || !StringUtils.hasText(config.clientSecret())) {
            throw new BusinessException(ResultCode.GITHUB_OAUTH_NOT_CONFIGURED);
        }
    }

    private GithubOAuthConfig oauthConfig(HttpServletRequest request) {
        String baseUrl = requestBaseUrl(request);
        return new GithubOAuthConfig(
                settingService.get(SettingKeys.GITHUB_CLIENT_ID, ""),
                settingService.get(SettingKeys.GITHUB_CLIENT_SECRET, ""),
                withDefault(settingService.get(SettingKeys.GITHUB_REDIRECT_URI, ""),
                        baseUrl + "/api/auth/github/callback"),
                withDefault(settingService.get(SettingKeys.GITHUB_FRONTEND_CALLBACK_URI, ""),
                        baseUrl + "/oauth/github/callback"));
    }

    private HttpClient httpClient() {
        return httpClientFactory.create(Duration.ofSeconds(10), HttpClient.Version.HTTP_1_1);
    }

    private String uniqueUsername(GithubProfile profile) {
        String base = profile.login().replaceAll("[^A-Za-z0-9_-]", "-");
        if (!StringUtils.hasText(base)) {
            base = "github-" + profile.id();
        }
        base = truncate(base, USERNAME_MAX_LENGTH);
        if (userService.getByUsername(base) == null) {
            return base;
        }

        String suffix = "-gh" + profile.id();
        int maxBaseLength = Math.max(1, USERNAME_MAX_LENGTH - suffix.length());
        String candidate = truncate(base, maxBaseLength) + suffix;
        if (userService.getByUsername(candidate) == null) {
            return candidate;
        }

        for (int i = 2; i < 100; i++) {
            String numberedSuffix = suffix + "-" + i;
            maxBaseLength = Math.max(1, USERNAME_MAX_LENGTH - numberedSuffix.length());
            candidate = truncate(base, maxBaseLength) + numberedSuffix;
            if (userService.getByUsername(candidate) == null) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.USERNAME_EXISTS);
    }

    private String frontendRedirect(String frontendCallbackUri, String ticket, String redirect, String error) {
        StringBuilder uri = new StringBuilder(frontendCallbackUri);
        char separator = frontendCallbackUri.contains("?") ? '&' : '?';
        if (StringUtils.hasText(ticket)) {
            uri.append(separator).append("ticket=").append(enc(ticket));
            separator = '&';
        }
        if (StringUtils.hasText(error)) {
            uri.append(separator).append("error=").append(enc(error));
            separator = '&';
        }
        uri.append(separator).append("redirect=").append(enc(sanitizeRedirect(redirect)));
        return uri.toString();
    }

    private String sanitizeRedirect(String redirect) {
        if (!StringUtils.hasText(redirect)) {
            return "/dashboard";
        }
        String trimmed = redirect.trim();
        if (!trimmed.startsWith("/") || trimmed.startsWith("//")
                || trimmed.startsWith("/api/") || trimmed.startsWith("/v1/")
                || trimmed.startsWith("/anthropic/") || trimmed.length() > 300) {
            return "/dashboard";
        }
        return trimmed;
    }

    private String requestBaseUrl(HttpServletRequest request) {
        String scheme = firstForwardedValue(request.getHeader("X-Forwarded-Proto"));
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        String host = firstForwardedValue(request.getHeader("X-Forwarded-Host"));
        if (!StringUtils.hasText(host)) {
            host = request.getHeader("Host");
        }
        if (!StringUtils.hasText(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && !(port == 80 && "http".equalsIgnoreCase(scheme))
                    && !(port == 443 && "https".equalsIgnoreCase(scheme))) {
                host += ":" + port;
            }
        }
        String prefix = firstForwardedValue(request.getHeader("X-Forwarded-Prefix"));
        if (!StringUtils.hasText(prefix)) {
            prefix = request.getContextPath();
        }
        return scheme + "://" + host + (StringUtils.hasText(prefix) ? prefix : "");
    }

    private String firstForwardedValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }

    private String withDefault(String value, String def) {
        return StringUtils.hasText(value) ? value.trim() : def;
    }

    private String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private String githubErrorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return ResultCode.GITHUB_OAUTH_FAILED.getMessage();
        }
        try {
            JsonNode json = objectMapper.readTree(body);
            String description = text(json, "error_description");
            if (StringUtils.hasText(description)) {
                return description;
            }
            String error = text(json, "error");
            if (StringUtils.hasText(error)) {
                return "GitHub OAuth error: " + error;
            }
        } catch (Exception e) {
            log.debug("Unable to parse GitHub OAuth error body", e);
        }
        return ResultCode.GITHUB_OAUTH_FAILED.getMessage();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private record GithubOAuthConfig(String clientId, String clientSecret, String redirectUri,
            String frontendCallbackUri) {
    }

    private record GithubProfile(Long id, String login, String email, String avatarUrl) {
    }
}
