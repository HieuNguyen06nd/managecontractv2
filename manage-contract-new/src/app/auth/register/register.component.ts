import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/services/auth.service';
import { RegisterRequest } from '../../core/models/auth.model';

@Component({
  selector: 'app-register',
  standalone: true,
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
  imports: [CommonModule, ReactiveFormsModule,FormsModule, RouterModule]
})
export class RegisterComponent {
  registerForm: FormGroup;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private toastr: ToastrService
  ) {
    this.registerForm = this.fb.group({
      fullName: ['', Validators.required],
      phone: ['', [Validators.required, Validators.pattern(/^[0-9]{10,11}$/)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', Validators.required],
      roles: [[]]
    }, { validators: this.passwordMatchValidator });
  }

  // Check mật khẩu khớp
  passwordMatchValidator(group: FormGroup) {
    const pass = group.get('password')?.value;
    const confirm = group.get('confirmPassword')?.value;
    return pass === confirm ? null : { notMatch: true };
  }

  // Submit form
  onSubmit() {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      this.toastr.warning('Vui lòng điền đầy đủ thông tin hợp lệ');
      return;
    }

    const req: RegisterRequest = {
      fullName: this.registerForm.value.fullName,
      phone: this.registerForm.value.phone,
      email: this.registerForm.value.email,
      password: this.registerForm.value.password,
      roles: this.registerForm.value.roles
    };

    this.loading = true;
    this.authService.register(req).subscribe({
      next: () => {
        this.toastr.success('Đăng ký thành công! Vui lòng kiểm tra email để xác nhận tài khoản.');
        this.loading = false;
        this.registerForm.reset();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error(err.error?.message || 'Đăng ký thất bại');
        this.loading = false;
      }
    });
  }
}
