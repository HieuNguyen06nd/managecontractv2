package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final StringRedisTemplate redisTemplate;
    private static final long EXPIRATION_TIME = 115;

    @Override
    public String generateOtp(String email) {
        // Tạo OTP gồm 6 chữ số ngẫu nhiên
        int otp = 100000 + new Random().nextInt(900000);
        String otpStr = String.valueOf(otp);
        // Lưu OTP vào Redis với TTL là 5 phút
        redisTemplate.opsForValue().set(email, otpStr, 5, TimeUnit.MINUTES);
        return otpStr;
    }

    @Override
    public boolean verifyOtp(String email, String otp) {
        String storedOtp = redisTemplate.opsForValue().get(email);
        if (storedOtp != null && storedOtp.equals(otp)) {
            // Xóa OTP sau khi xác thực thành công
            redisTemplate.delete(email);
            return true;
        }
        return false;
    }

    public void clearOtp(String email) {
        redisTemplate.delete("OTP:" + email);
    }

}
