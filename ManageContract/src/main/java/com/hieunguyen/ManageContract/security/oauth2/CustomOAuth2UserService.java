package com.hieunguyen.ManageContract.security.oauth2;

import com.hieunguyen.ManageContract.common.constants.StatusUser;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Role;
import com.hieunguyen.ManageContract.entity.UserRole;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.repository.RoleRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.repository.UserRoleRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final AuthAccountRepository authAccountRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;


    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = new DefaultOAuth2UserService().loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = getProviderId(provider, attributes);
        String email = (String) attributes.get("email");
        String name = (String) attributes.getOrDefault("name", "Unknown");

        if (providerId == null) {
            throw new OAuth2AuthenticationException("Không tìm thấy ID từ " + provider);
        }

        AuthAccount account = findOrCreateAuthAccount(provider, providerId, email, name);

        List<UserRole> userRoles = userRoleRepository.findByAccount(account);

        Set<SimpleGrantedAuthority> authorities = userRoles.stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getRoleKey()))
                .collect(Collectors.toSet());

        return new DefaultOAuth2User(authorities, attributes, "email");
    }


    private String getProviderId(String provider, Map<String, Object> attributes) {
        return switch (provider) {
            case "google" -> (String) attributes.get("sub");
            case "facebook" -> (String) attributes.get("id");
            default -> null;
        };
    }

    private AuthAccount findOrCreateAuthAccount(String provider, String providerId, String email, String name) {
        Optional<AuthAccount> existingAccount = switch (provider) {
            case "google" -> authAccountRepository.findByGoogleId(providerId);
            case "facebook" -> authAccountRepository.findByFacebookId(providerId);
            default -> Optional.empty();
        };

        if (existingAccount.isPresent()) return existingAccount.get();

        if (email == null || email.isEmpty()) {
            email = provider + "_" + providerId + "@noemail.com";
        }

        AuthAccount account = authAccountRepository.findByEmail(email).orElse(null);
        boolean isNew = false;

        if (account == null) {
            account = new AuthAccount();
            account.setEmail(email);
            account.setPassword("");
            account.setStatus(StatusUser.ACTIVE);
            account.setEmailVerified(true);
            account.setCreatedAt(LocalDateTime.now());
            isNew = true;
        }

        // Cập nhật providerId
        if (provider.equals("google")) account.setGoogleId(providerId);
        if (provider.equals("facebook")) account.setFacebookId(providerId);

        account.setUpdatedAt(LocalDateTime.now());
        AuthAccount savedAccount = authAccountRepository.save(account);

//        if (isNew) {
//            // Gán role mặc định
//            Role patientRole = roleRepository.findByRoleKey("PATIENT")
//                    .orElseThrow(() -> new RuntimeException("Role PATIENT không tồn tại"));
//            UserRole userRole = new UserRole();
//            userRole.setAccount(savedAccount);
//            userRole.setRole(patientRole);
//            userRole.setCreatedAt(LocalDateTime.now());
//            userRoleRepository.save(userRole);
//
//             Tạo User đi kèm
//            Patient patient = new Patient();
//            patient.setAccount(savedAccount);
//            patient.setFullName(name);
//            patient.setPhone(null);
//            patientRepository.save(patient);
//        }

        return savedAccount;
    }
}
