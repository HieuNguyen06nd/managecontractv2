package com.hieunguyen.ManageContract.security.oauth2;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.UserRoleRepository;
import com.hieunguyen.ManageContract.security.jwt.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final AuthAccountRepository authAccountRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email = oauthUser.getAttribute("email");

        if (email == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Không lấy được email từ OAuth2 provider\"}");
            response.getWriter().flush();
            return;
        }

        AuthAccount account = authAccountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản chưa tồn tại trong hệ thống"));

        // Lấy danh sách vai trò của người dùng
        List<String> roleKeys = userRoleRepository.findByAccount(account)
                .stream()
                .map(userRole -> userRole.getRole().getRoleKey())
                .collect(Collectors.toList());

        String token = jwtUtil.generateToken(email, roleKeys);

        response.setContentType("application/json");
        response.getWriter().write("{\"token\": \"" + token + "\"}");
        response.getWriter().flush();
    }
}
