package com.example.ClaimShieldX.common.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;


public class CookieManager {
    public static String REFRESH_TOKEN_KEY = "refreshToken";
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
}
