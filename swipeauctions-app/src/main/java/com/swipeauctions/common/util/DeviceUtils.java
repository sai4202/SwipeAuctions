package com.swipeauctions.common.util;

public class DeviceUtils {

    public static String extractDeviceName(
            String userAgent
    ) {

        if (userAgent == null) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

        String browser = "Unknown Browser";
        String os = "Unknown OS";

        // Browser detection
        if (ua.contains("edg")) {
            browser = "Edge";
        }
        else if (ua.contains("chrome")) {
            browser = "Chrome";
        }
        else if (ua.contains("firefox")) {
            browser = "Firefox";
        }
        else if (ua.contains("safari")
                && !ua.contains("chrome")) {

            browser = "Safari";
        }

        // Operating System detection
        if (ua.contains("windows")) {
            os = "Windows";
        }
        else if (ua.contains("android")) {
            os = "Android";
        }
        else if (ua.contains("iphone")) {
            os = "iPhone";
        }
        else if (ua.contains("mac")) {
            os = "Mac";
        }
        else if (ua.contains("linux")) {
            os = "Linux";
        }

        return browser + " on " + os;
    }
}