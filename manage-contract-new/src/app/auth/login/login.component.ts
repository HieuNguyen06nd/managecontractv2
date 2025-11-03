import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';

import { AuthService } from '../../core/services/auth.service';
import { LoginRequest } from '../../core/models/auth.model';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  // UI state
  loginMode: 'password' | 'otp' = 'password';
  loading = false;
  otpSent = false;

  // password mode
  emailOrPhone = '';
  password = '';

  // otp mode
  otpEmail = '';
  otpCode = '';

  constructor(
    private authService: AuthService,
    private toastr: ToastrService,
    private router: Router
  ) {}

  switchMode(mode: 'password' | 'otp') {
    this.loginMode = mode;
    // reset nhẹ phần OTP khi chuyển chế độ
    if (mode === 'otp') {
      this.otpSent = false;
      this.otpCode = '';
    }
  }

  // ======== LOGIN (PASSWORD) ========
  loginWithPassword() {
    const emailOrPhone = this.emailOrPhone?.trim();
    const password = this.password?.trim();

    if (!emailOrPhone || !password) {
      this.toastr.warning('Vui lòng nhập đầy đủ Email/SĐT và Mật khẩu');
      return;
    }

    const req: LoginRequest = { emailOrPhone, password };
    this.loading = true;

    this.authService.login(req).subscribe({
      next: (res) => {
        try {
          const data: any = res?.data;
          if (!data) throw new Error('Phản hồi không hợp lệ');

          // Bắt case bắt buộc đổi mật khẩu lần đầu
          if (data.mustChangePassword) {
            const cpToken = data.changePasswordToken;
            if (!cpToken) throw new Error('Thiếu token đổi mật khẩu');
            this.toastr.info('Vui lòng đổi mật khẩu để kích hoạt tài khoản');
            this.router.navigate(['/auth/change-password'], { queryParams: { token: cpToken } });
            return;
          }

          // Lưu session (decode JWT + roles/authorities)
          this.authService.setSessionFromResponse(res);

          this.toastr.success('Đăng nhập thành công!');
          this.router.navigateByUrl('/dashboard');
        } catch (e: any) {
          this.toastr.error(e?.message || 'Đăng nhập thất bại.');
        } finally {
          this.loading = false;
        }
      },
      error: (err) => {
        this.loading = false;
        console.error('Lỗi đăng nhập:', err);
        this.toastr.error(err?.error?.message || 'Đăng nhập thất bại.');
      }
    });
  }

  // ======== GỬI OTP ========
  sendOtp() {
    const email = this.otpEmail?.trim();
    if (!email) {
      this.toastr.warning('Vui lòng nhập email trước khi gửi OTP');
      return;
    }
    this.loading = true;
    this.authService.sendOtp(email).subscribe({
      next: () => {
        this.loading = false;
        this.otpSent = true;
        this.toastr.success('OTP đã được gửi tới email của bạn');
      },
      error: (err) => {
        this.loading = false;
        console.error('Lỗi gửi OTP:', err);
        this.toastr.error(err?.error?.message || 'Không gửi được OTP.');
      }
    });
  }

  // ======== LOGIN (OTP) ========
  loginWithOtp() {
    const emailOrPhone = this.otpEmail?.trim();
    const otp = this.otpCode?.trim();

    if (!emailOrPhone || !otp) {
      this.toastr.warning('Vui lòng nhập đầy đủ Email và OTP');
      return;
    }

    const req: LoginRequest = { emailOrPhone, otp };
    this.loading = true;

    this.authService.login(req).subscribe({
      next: (res) => {
        try {
          const data: any = res?.data;
          if (!data) throw new Error('Phản hồi không hợp lệ');

          // OTP login vẫn có thể bị bắt đổi mật khẩu (tuỳ BE)
          if (data.mustChangePassword) {
            const cpToken = data.changePasswordToken;
            if (!cpToken) throw new Error('Thiếu token đổi mật khẩu');
            this.toastr.info('Vui lòng đổi mật khẩu để kích hoạt tài khoản');
            this.router.navigate(['/auth/change-password'], { queryParams: { token: cpToken } });
            return;
          }

          this.authService.setSessionFromResponse(res);

          this.toastr.success('Đăng nhập bằng OTP thành công!');
          this.router.navigateByUrl('/dashboard');
        } catch (e: any) {
          this.toastr.error(e?.message || 'Đăng nhập OTP thất bại.');
        } finally {
          this.loading = false;
        }
      },
      error: (err) => {
        this.loading = false;
        console.error('Lỗi đăng nhập OTP:', err);
        this.toastr.error(err?.error?.message || 'Đăng nhập OTP thất bại.');
      }
    });
  }
}
