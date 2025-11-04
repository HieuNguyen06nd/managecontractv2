package com.hieunguyen.ManageContract.security.jwt;

import com.hieunguyen.ManageContract.security.auth.CustomUserDetails;
import com.hieunguyen.ManageContract.security.auth.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();

        // 1) Kiểm tra chữ ký + hạn. Không hợp lệ thì bỏ qua (để endpoint xử lý 401/403).
        if (!jwtUtil.validateToken(jwt)) {
            log.warn("JWT không hợp lệ hoặc đã hết hạn");
            filterChain.doFilter(request, response);
            return;
        }

        // 2) Nếu đã có Authentication thì không set lại
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = null;
        Long accountId = null;

        // 3) Ưu tiên claim email
        try {
            email = jwtUtil.extractEmail(jwt); // có thể trả null
        } catch (Exception e) {
            log.debug("Không lấy được email từ JWT: {}", e.getMessage());
        }

        // 4) Lấy accountId từ claim hoặc sub dạng số
        try {
            accountId = jwtUtil.extractAccountId(jwt); // có thể trả null
        } catch (Exception e) {
            log.debug("Không lấy được accountId từ JWT: {}", e.getMessage());
        }

        // 5) Fallback: dùng subject (email hoặc id)
        if (email == null && accountId == null) {
            try {
                String sub = jwtUtil.extractSubject(jwt);
                if (sub != null) {
                    if (sub.matches("\\d+")) accountId = Long.valueOf(sub);
                    else email = sub; // sub là email
                }
            } catch (Exception e) {
                log.debug("Không đọc được subject từ JWT: {}", e.getMessage());
            }
        }

        try {
            CustomUserDetails userDetails;
            if (email != null) {
                userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(email);
            } else if (accountId != null) {
                userDetails = userDetailsService.loadUserByAccountId(accountId);
            } else {
                throw new BadCredentialsException("JWT thiếu cả email và accountId");
            }

            var authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities() != null ? userDetails.getAuthorities() : Collections.emptyList()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            log.info("Đã xác thực: {}", userDetails.getUsername());

        } catch (Exception ex) {
            // Không đẩy lỗi ra ngoài để tránh 500
            log.error("Lỗi xác thực JWT: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
