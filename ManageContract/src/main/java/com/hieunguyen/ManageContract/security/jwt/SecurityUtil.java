package com.hieunguyen.ManageContract.security.jwt;

import com.hieunguyen.ManageContract.security.auth.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;

@Component
public class SecurityUtil {

    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            return cud.getUsername(); // CustomUserDetails.getUsername() nên là email
        }
        if (principal instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            if (email == null) email = jwt.getClaimAsString("preferred_username");
            if (email == null && jwt.getSubject() != null && jwt.getSubject().contains("@")) {
                email = jwt.getSubject();
            }
            if (email != null) return email;
        }
        // fallback
        String name = auth.getName();
        return (name != null && name.contains("@")) ? name : null;
    }

    /** Lấy accountId nếu có claim hoặc principal chứa */
    public static Long getCurrentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            // nếu bạn có phương thức getAccountId() thì ưu tiên dùng
            try {
                var m = cud.getClass().getMethod("getAccountId");
                Object v = m.invoke(cud);
                if (v != null) return Long.valueOf(String.valueOf(v));
            } catch (Exception ignored) {}
        }
        if (principal instanceof Jwt jwt) {
            Object accId = jwt.getClaim("accountId");
            if (accId != null) return Long.valueOf(String.valueOf(accId));
            String sub = jwt.getSubject();
            if (sub != null && sub.matches("\\d+")) return Long.valueOf(sub);
        }
        return null;
    }

    /** Nếu bạn còn cần employeeId như trước */
    public static Long getCurrentEmployeeId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails cud) {
            try {
                return cud.getEmployeeId();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
