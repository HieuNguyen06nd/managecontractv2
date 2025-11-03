import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { finalize } from 'rxjs/operators';

import { AuthService } from '../../core/services/auth.service';
import { LoginRequest } from '../../core/models/auth.model';

type View = 'password' | 'otp' | 'firstChange';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  // ======= UI state =======
  view: View = 'password';
  loading = false;
  otpSent = false;

  // password mode
  emailOrPhone = '';
  password = '';

  // otp mode
  otpEmail = '';
  otpCode = '';

  // first-change mode
  firstChangeToken = '';
  newPassword = '';
  confirmPassword = '';

  constructor(
    private authService: AuthService,
    private toastr: ToastrService,
    private router: Router
  ) {}

  switchMode(mode: 'password' | 'otp') {
    this.view = mode;
    if (mode === 'otp') {
      this.otpSent = false;
      this.otpCode = '';
    }
  }

  // =========================
  // LOGIN (PASSWORD)
  // =========================
  loginWithPassword() {
    const emailOrPhone = this.emailOrPhone?.trim();
    const password = this.password?.trim();
    if (!emailOrPhone || !password) {
      this.toastr.warning('Vui lòng nhập đầy đủ Email/SĐT và Mật khẩu');
      return;
    }

    const req: LoginRequest = { emailOrPhone, password };
    this.loading = true;

    this.authService.login(req)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (res) => {
          const data: any = res?.data;
          if (!data) { this.toastr.error('Phản hồi không hợp lệ'); return; }

          // Bị yêu cầu đổi mật khẩu lần đầu?
          const requireChange = (data.requirePasswordChange ?? data.mustChangePassword) === true;
          if (requireChange) {
            const cpToken = data.changePasswordToken ?? data.change_password_token;
            if (!cpToken) { this.toastr.error('Thiếu token đổi mật khẩu'); return; }
            this.firstChangeToken = cpToken;
            this.newPassword = '';
            this.confirmPassword = '';
            this.view = 'firstChange';
            this.toastr.info('Vui lòng đổi mật khẩu để kích hoạt tài khoản');
            return; // không lưu session ở đây
          }

          if (!data.accessToken || !data.refreshToken) {
            this.toastr.error('Thiếu accessToken/refreshToken');
            return;
          }
          this.authService.setSessionFromResponse(res);
          this.toastr.success('Đăng nhập thành công!');
          this.router.navigateByUrl('/dashboard');
        },
        error: (err) => {
          console.error('Lỗi đăng nhập:', err);
          this.toastr.error(err?.error?.message || 'Đăng nhập thất bại.');
        }
      });
  }

  // =========================
  // GỬI OTP
  // =========================
  sendOtp() {
    const email = this.otpEmail?.trim();
    if (!email) { this.toastr.warning('Vui lòng nhập email trước khi gửi OTP'); return; }
    if (this.loading) return;
    this.loading = true;

    this.authService.sendOtp(email)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: () => { this.otpSent = true; this.toastr.success('OTP đã được gửi tới email của bạn'); },
        error: (err) => {
          console.error('Lỗi gửi OTP:', err);
          this.toastr.error(err?.error?.message || 'Không gửi được OTP.');
        }
      });
  }

  // =========================
  // LOGIN (OTP)
  // =========================
  loginWithOtp() {
    const emailOrPhone = this.otpEmail?.trim();
    const otp = this.otpCode?.trim();
    if (!emailOrPhone || !otp) { this.toastr.warning('Vui lòng nhập đầy đủ Email và OTP'); return; }

    const req: LoginRequest = { emailOrPhone, otp };
    this.loading = true;

    this.authService.login(req)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (res) => {
          const data: any = res?.data;
          if (!data) { this.toastr.error('Phản hồi không hợp lệ'); return; }

          // Bị yêu cầu đổi mật khẩu lần đầu?
          const requireChange = (data.requirePasswordChange ?? data.mustChangePassword) === true;
          if (requireChange) {
            const cpToken = data.changePasswordToken ?? data.change_password_token;
            if (!cpToken) { this.toastr.error('Thiếu token đổi mật khẩu'); return; }
            this.firstChangeToken = cpToken;
            this.newPassword = '';
            this.confirmPassword = '';
            this.view = 'firstChange';
            this.toastr.info('Vui lòng đổi mật khẩu để kích hoạt tài khoản');
            return;
          }

          if (!data.accessToken || !data.refreshToken) {
            this.toastr.error('Thiếu accessToken/refreshToken');
            return;
          }
          this.authService.setSessionFromResponse(res);
          this.toastr.success('Đăng nhập bằng OTP thành công!');
          this.router.navigateByUrl('/dashboard');
        },
        error: (err) => {
          console.error('Lỗi đăng nhập OTP:', err);
          this.toastr.error(err?.error?.message || 'Đăng nhập OTP thất bại.');
        }
      });
  }

  // =========================
  // FIRST CHANGE PASSWORD (ngay trong LoginComponent)
  // =========================
  submitFirstChange() {
    if (!this.firstChangeToken) { this.toastr.error('Thiếu token đổi mật khẩu'); return; }
    const pw1 = this.newPassword?.trim();
    const pw2 = this.confirmPassword?.trim();
    if (!pw1 || !pw2) { this.toastr.warning('Vui lòng nhập đầy đủ mật khẩu'); return; }
    if (pw1 !== pw2) { this.toastr.warning('Mật khẩu nhập lại không khớp'); return; }
    if (pw1.length < 6) { this.toastr.warning('Mật khẩu tối thiểu 6 ký tự'); return; }

    this.loading = true;
    this.authService.firstChangePassword(pw1, this.firstChangeToken)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (res) => {
          // Nếu BE trả luôn tokens mới -> đăng nhập thẳng
          const data: any = res?.data;
          if (data?.accessToken && data?.refreshToken) {
            try {
              this.authService.setSessionFromResponse(res);
              this.toastr.success('Đổi mật khẩu & kích hoạt thành công!');
              this.router.navigateByUrl('/dashboard');
              return;
            } catch {}
          }
          // Nếu không có token, yêu cầu đăng nhập lại
          this.toastr.success('Đổi mật khẩu thành công, vui lòng đăng nhập lại.');
          this.view = 'password';
          this.firstChangeToken = '';
          this.password = '';
        },
        error: (err) => {
          console.error('Đổi mật khẩu lần đầu lỗi:', err);
          this.toastr.error(err?.error?.message || 'Đổi mật khẩu thất bại');
        }
      });
  }

  // Quay về màn đăng nhập
  cancelFirstChange() {
    this.view = 'password';
    this.firstChangeToken = '';
    this.newPassword = '';
    this.confirmPassword = '';
  }
}
