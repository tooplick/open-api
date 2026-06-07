package com.aiopen.platform.modules.auth.github;

import com.aiopen.platform.modules.user.dto.GithubAuthorizeResponse;
import com.aiopen.platform.modules.user.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface GithubOAuthService {

    GithubAuthorizeResponse buildAuthorizeUrl(String redirect, HttpServletRequest request);

    String handleCallback(String code, String state, String error, String errorDescription,
            HttpServletRequest request);

    LoginResponse exchangeTicket(String ticket);
}
