import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  LoginRequest,
  AuthResponse,
  RegisterRequest,
  RegisterResponse,
  AuthProfileResponse
} from '../models/auth.model';

import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private baseUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  // Đăng nhập bằng mật khẩu
  loginWithPassword(req: LoginRequest): Observable<ResponseData<AuthResponse>> {
    return this.http.post<ResponseData<AuthResponse>>(`${this.baseUrl}/login`, req);
  }

  // Đăng nhập bằng OTP
  loginWithOtp(req: LoginRequest): Observable<ResponseData<AuthResponse>> {
    return this.http.post<ResponseData<AuthResponse>>(`${this.baseUrl}/login`, req);
  }

  // Gửi OTP
  sendOtp(emailOrPhone: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/send-otp?email=${emailOrPhone}`,
      {}
    );
  }

  // Xác minh OTP
  verifyOtp(email: string, otp: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/verify-otp?email=${email}&otp=${otp}`,
      {}
    );
  }

  // Đặt lại mật khẩu bằng OTP
  resetPassword(data: {
    email: string;
    otp: string;
    newPassword: string;
  }): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/forgot-password/reset`,
      data
    );
  }

  // Đăng ký tài khoản
  register(req: RegisterRequest): Observable<ResponseData<RegisterResponse>> {
    return this.http.post<ResponseData<RegisterResponse>>(
      `${this.baseUrl}/register`,
      req
    );
  }

  // Lấy thông tin user hiện tại (/me)
  getProfile(): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.get<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me`);
  }

  logout(email: string): Observable<ResponseData<string>> {
    return this.http.post<ResponseData<string>>(
      `${this.baseUrl}/logout?email=${email}`,
      {}
    );
  }

  firstChangePassword(newPassword: string, changePasswordToken: string)
    : Observable<ResponseData<AuthResponse>> {
    return this.http.post<ResponseData<AuthResponse>>(
      `${this.baseUrl}/first-change-password`,
      { newPassword },
      { headers: { Authorization: `Bearer ${changePasswordToken}` } }
    );
  }


}
