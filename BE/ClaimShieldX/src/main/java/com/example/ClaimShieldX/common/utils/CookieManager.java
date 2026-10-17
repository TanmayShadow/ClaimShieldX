package com.example.ClaimShieldX.common.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

public class CookieManager {
    public static final String REFRESH_TOKEN_KEY = "refreshToken";

    // Must be a prefix of the real routes (/refresh and /logout), or the browser never sends the cookie.
    public static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    public static String getCookie(HttpServletRequest request, String key){
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (key.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public static ResponseCookie refreshCookie(String token, long maxAgeSeconds){
        return base(token).maxAge(maxAgeSeconds).build();
    }

    public static ResponseCookie clearedRefreshCookie(){
        return base("").maxAge(0).build();
    }

    private static ResponseCookie.ResponseCookieBuilder base(String value){
        return ResponseCookie.from(REFRESH_TOKEN_KEY, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH);
    }
}
