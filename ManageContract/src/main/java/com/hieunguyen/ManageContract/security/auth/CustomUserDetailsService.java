package com.hieunguyen.ManageContract.security.auth;


import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AuthAccountRepository authAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        AuthAccount account = authAccountRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));

        return new CustomUserDetails(account);
    }
    public CustomUserDetails loadUserByAccountId(Long accountId) {
        var account = authAccountRepository.findById(accountId)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + accountId));
        // Từ account lấy email rồi reuse luồng loadUserByUsername
        return (CustomUserDetails) loadUserByUsername(account.getEmail());
    }
}
