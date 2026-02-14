package org.ost.advertisement.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordEncoderUtil {

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private PasswordEncoderUtil() {
    }

    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }
}
