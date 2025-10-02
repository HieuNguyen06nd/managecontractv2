import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/services/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './forget-password.component.html',
  styleUrls: ['./forget-password.component.scss']
})
export class ForgetPasswordComponent implements OnDestroy {
  step = 1; // Bước hiện tại
  timer = '05:00';
  countdown: any;
  loading = false;

  emailForm: FormGroup;
  otpForm: FormGroup;
  resetForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private toastr: ToastrService,
    private authService: AuthService,
    private router: Router   
  ) {
    // Form nhập email
    this.emailForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]]
    });

    // Form nhập OTP
    this.otpForm = this.fb.group({
      otp: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });

    // Form đặt lại mật khẩu
    this.resetForm = this.fb.group({
      newPassword: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required]
    }, { validators: this.passwordMatchValidator });
  }

  // Validator: mật khẩu khớp
  passwordMatchValidator(group: FormGroup) {
    const pass = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { notMatch: true };
  }

  // Step 1: gửi OTP
  sendOtp() {
    if (this.emailForm.invalid) {
      this.toastr.error('Vui lòng nhập email hợp lệ');
      return;
    }

    this.loading = true;
    this.authService.sendOtp(this.emailForm.value.email).subscribe({
      next: (res) => {
        this.toastr.success(res.message || 'OTP đã gửi đến email');
        this.step = 2;
        this.startTimer();
        this.loading = false;
      },
      error: (err) => {
        this.toastr.error(err.error?.message || 'Không gửi được OTP');
        this.loading = false;
      }
    });
  }

  // Step 2: xác thực OTP
  verifyOtp() {
    if (this.otpForm.invalid) {
      this.toastr.error('Vui lòng nhập OTP hợp lệ');
      return;
    }

    this.loading = true;
    this.authService.verifyOtp(this.emailForm.value.email, this.otpForm.value.otp).subscribe({
      next: (res) => {
        this.toastr.success(res.message || 'Xác thực OTP thành công');
        this.step = 3;
        clearInterval(this.countdown);
        this.loading = false;
      },
      error: (err) => {
        this.toastr.error(err.error?.message || 'OTP không chính xác hoặc đã hết hạn');
        this.loading = false;
      }
    });
  }

  // Step 3: reset mật khẩu
   resetPassword() {
    if (this.resetForm.invalid) {
      this.toastr.error('Vui lòng nhập mật khẩu hợp lệ');
      return;
    }

    const req = {
      email: this.emailForm.value.email,
      otp: this.otpForm.value.otp,
      newPassword: this.resetForm.value.newPassword
    };

    this.loading = true;
    this.authService.resetPassword(req).subscribe({
      next: (res) => {
        this.toastr.success(res.message || 'Đặt lại mật khẩu thành công');
        this.loading = false;

        // 👉 Sau 1s quay lại màn login
        setTimeout(() => {
          this.router.navigate(['/auth/login']);
        }, 1000);
      },
      error: (err) => {
        this.toastr.error(err.error?.message || 'Đặt lại mật khẩu thất bại');
        this.loading = false;
      }
    });
  }

  // Gửi lại OTP
  resendOtp() {
    this.sendOtp();
  }

  // Đếm ngược OTP
  startTimer() {
    let timeLeft = 300; // 5 phút
    clearInterval(this.countdown);
    this.countdown = setInterval(() => {
      if (timeLeft <= 0) {
        clearInterval(this.countdown);
        this.timer = '00:00';
        this.toastr.error('OTP đã hết hạn, vui lòng gửi lại');
      } else {
        const m = String(Math.floor(timeLeft / 60)).padStart(2, '0');
        const s = String(timeLeft % 60).padStart(2, '0');
        this.timer = `${m}:${s}`;
        timeLeft--;
      }
    }, 1000);
  }

  ngOnDestroy() {
    if (this.countdown) {
      clearInterval(this.countdown);
    }
  }
}
