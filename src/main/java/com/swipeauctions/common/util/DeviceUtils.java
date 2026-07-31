package com.swipeauctions.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeviceUtils {

    private static final int MAX_CLIENT_MODEL_LENGTH = 40;

    // Android's raw User-Agent still embeds the model between "Android <ver>; " and the next ";"/")"
    // on most browsers that don't send User-Agent Client Hints (Firefox for Android, older Chrome).
    // "K", "wv" and blank are Chrome's own reduced-UA placeholders, not a real model — skip those.
    private static final Pattern ANDROID_MODEL = Pattern.compile("Android [^;]+;\\s*([^;)]+)[;)]");

    // iOS never exposes the exact hardware model to any browser (Apple restriction, not fixable from
    // here) — the OS version is the most specific thing honestly obtainable from the User-Agent.
    private static final Pattern IOS_VERSION = Pattern.compile("(?:iPhone )?OS (\\d+)_(\\d+)");

    /**
     * Best available device label. Prefers {@code clientModel} — the exact hardware model (e.g.
     * "Pixel 9", "SM-S928B") read client-side via the User-Agent Client Hints API
     * (`navigator.userAgentData.getHighEntropyValues(['model'])`), which is the only source that can
     * ever produce a real model name. That API only exists on Chromium-based browsers on Android; it
     * does not exist on iOS/Safari at all (Apple never exposes hardware model to any web page, by
     * design — no server-side parsing trick can recover what the platform never sends), and isn't
     * present on desktop. Falls back to User-Agent parsing for everyone else.
     */
    public static String buildDeviceName(String userAgent, String clientModel) {

        String sanitized = sanitizeClientModel(clientModel);

        if (sanitized != null) {
            return sanitized;
        }

        return extractDeviceName(userAgent);
    }

    private static String sanitizeClientModel(String clientModel) {

        if (clientModel == null) {
            return null;
        }

        // Strip control/non-printable characters a malicious client could inject — this is just a
        // display label (React escapes it on render), but keep the stored value sane regardless.
        String cleaned = clientModel.replaceAll("[\\p{Cntrl}]", "").trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        return cleaned.length() > MAX_CLIENT_MODEL_LENGTH
                ? cleaned.substring(0, MAX_CLIENT_MODEL_LENGTH)
                : cleaned;
    }

    public static String extractDeviceName(
            String userAgent
    ) {

        if (userAgent == null) {
            return "Unknown Device";
        }

        String ua = userAgent.toLowerCase();

        String browser = "Unknown Browser";

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

        // Operating System detection — Android tries to recover a real model from the raw UA first;
        // iOS can only ever report OS version (see IOS_VERSION javadoc above on buildDeviceName).
        String os;
        if (ua.contains("windows")) {
            os = "Windows";
        }
        else if (ua.contains("android")) {
            os = androidModelOrGeneric(userAgent);
        }
        else if (ua.contains("ipad")) {
            os = "iPad" + iosVersionSuffix(userAgent);
        }
        else if (ua.contains("iphone")) {
            os = "iPhone" + iosVersionSuffix(userAgent);
        }
        else if (ua.contains("mac")) {
            os = "Mac";
        }
        else if (ua.contains("linux")) {
            os = "Linux";
        }
        else {
            os = "Unknown OS";
        }

        return browser + " on " + os;
    }

    private static String androidModelOrGeneric(String userAgent) {

        Matcher m = ANDROID_MODEL.matcher(userAgent);

        if (m.find()) {
            String model = m.group(1).trim();
            // Chrome's reduced-UA placeholders for a withheld model — not a real device name.
            if (!model.isEmpty() && !model.equalsIgnoreCase("K") && !model.equalsIgnoreCase("wv")) {
                return model;
            }
        }

        return "Android";
    }

    private static String iosVersionSuffix(String userAgent) {

        Matcher m = IOS_VERSION.matcher(userAgent);

        if (m.find()) {
            return " (iOS " + m.group(1) + "." + m.group(2) + ")";
        }

        return "";
    }
}
