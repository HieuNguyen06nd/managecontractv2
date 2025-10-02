import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router'; 
import { AuthService } from '../../core/services/auth.service';
import { LoginRequest } from '../../core/models/auth.model';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
  imports: [CommonModule, FormsModule]
})
export class LoginComponent {
  loginMode: 'password' | 'otp' = 'password';

  emailOrPhone = '';
  password = '';

  otpEmail = '';
  otpCode = '';
  otpSent = false;

  constructor(private authService: AuthService,
    private toastr: ToastrService,
    private router: Router 
  ) {}

  switchMode(mode: 'password' | 'otp') {
    this.loginMode = mode;
    if (mode === 'otp') {
      this.otpSent = false;
      this.otpCode = '';
    }
  }

  loginWithPassword() {
    if (!this.emailOrPhone || !this.password) {
      this.toastr.warning('Vui lòng điền đầy đủ thông tin');
      return;
    }

    const req: LoginRequest = {
      emailOrPhone: this.emailOrPhone,
      password: this.password,
    };

    this.authService.loginWithPassword(req).subscribe({
      next: (res) => {
        console.log('Đăng nhập thành công:', res);
        this.toastr.success('Đăng nhập thành công!');

        // Lưu token và refreshToken
        localStorage.setItem('token', res.data.token);
        localStorage.setItem('refreshToken', res.data.refreshToken);

        // Lưu user info (id, roles)
        localStorage.setItem('userId', res.data.userId.toString());
        localStorage.setItem('roles', JSON.stringify(res.data.roles));
        this.router.navigate(['/dasboad']); 
      },
      error: (err) => {
        console.error('Lỗi đăng nhập:', err);
        this.toastr.error('Sai tài khoản hoặc mật khẩu!');
      },
    });
  }

  sendOtp() {
    if (!this.otpEmail) {
      this.toastr.warning('Vui lòng nhập email trước khi gửi OTP');
      return;
    }

    this.authService.sendOtp(this.otpEmail).subscribe({
      next: () => {
        alert('OTP đã được gửi!');
        this.otpSent = true;
      },
      error: (err) => {
        console.error('Lỗi gửi OTP:', err);
         this.toastr.error('Không gửi được OTP.');
      },
    });
  }

  loginWithOtp() {
    if (!this.otpEmail || !this.otpCode) {
      this.toastr.warning('Vui lòng nhập đầy đủ thông tin');
      return;
    }

    const req: LoginRequest = {
      emailOrPhone: this.otpEmail,
      otp: this.otpCode,
    };

    this.authService.loginWithOtp(req).subscribe({
      next: (res) => {
        console.log('Đăng nhập OTP thành công:', res);
        this.toastr.success('Đăng nhập bằng OTP thành công!');

        //  Lưu token và refreshToken
        localStorage.setItem('token', res.data.token);
        localStorage.setItem('refreshToken', res.data.refreshToken);

        // Lưu user info (id, roles)
        localStorage.setItem('userId', res.data.userId.toString());
        localStorage.setItem('roles', JSON.stringify(res.data.roles));

        this.router.navigate(['/dasboad']); 
      },
      error: (err) => {
        console.error('Lỗi đăng nhập OTP:', err);
        this.toastr.error('Đăng nhập OTP thất bại.');
      },
    });
  }
}
