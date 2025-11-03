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

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  // ==== Login dùng chung cho cả password & OTP ====
  login(req: LoginRequest): Observable<ResponseData<AuthResponse>> {
    // BE /api/auth/login hỗ trợ cả mật khẩu và OTP (truyền field nào thì dùng field đó)
    return this.http.post<ResponseData<AuthResponse>>(`${this.baseUrl}/login`, req);
  }

  // Gửi OTP
  sendOtp(emailOrPhone: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/send-otp?email=${encodeURIComponent(emailOrPhone)}`,
      {}
    );
  }

  // Xác minh OTP (nếu cần dùng riêng)
  verifyOtp(email: string, otp: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/verify-otp?email=${encodeURIComponent(email)}&otp=${encodeURIComponent(otp)}`,
      {}
    );
  }

  // Đặt lại mật khẩu bằng OTP
  resetPassword(data: { email: string; otp: string; newPassword: string; })
    : Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/forgot-password/reset`,
      data
    );
  }

  // Đăng ký
  register(req: RegisterRequest): Observable<ResponseData<RegisterResponse>> {
    return this.http.post<ResponseData<RegisterResponse>>(
      `${this.baseUrl}/register`,
      req
    );
  }

  // Tùy BE có /me hay chưa
  getProfile(): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.get<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me`);
  }

  // Đăng xuất BE (tùy dùng)
  logout(email: string): Observable<ResponseData<string>> {
    return this.http.post<ResponseData<string>>(
      `${this.baseUrl}/logout?email=${encodeURIComponent(email)}`,
      {}
    );
  }

  // Đổi mật khẩu lần đầu
  firstChangePassword(newPassword: string, changePasswordToken: string)
    : Observable<ResponseData<AuthResponse>> {
    return this.http.post<ResponseData<AuthResponse>>(
      `${this.baseUrl}/first-change-password`,
      { newPassword },
      { headers: { Authorization: `Bearer ${changePasswordToken}` } }
    );
  }

  // ========= SESSION HELPERS =========

  /** Lưu session từ response login (phù hợp LoginComponent) */
  setSessionFromResponse(res: ResponseData<AuthResponse>): void {
    const data = res?.data as any;
    if (!data) throw new Error('Phản hồi không hợp lệ');

    const accessToken: string | undefined = data.accessToken;
    const refreshToken: string | undefined = data.refreshToken;
    const userId: number | string | undefined = data.userId;

    if (!accessToken || !refreshToken) {
      throw new Error('Thiếu accessToken/refreshToken');
    }

    localStorage.setItem('token', accessToken);
    localStorage.setItem('refreshToken', refreshToken);
    if (userId != null) localStorage.setItem('userId', String(userId));

    // Ưu tiên roles/authorities/permissions từ server trả về
    const fromApiRoles = Array.isArray(data.roles) ? data.roles : null;            // có thể là [{roleKey: 'ADMIN'}, ...]
    const fromApiAuthorities = Array.isArray(data.authorities) ? data.authorities : null; // ['APPROVE_CONTRACT', 'ROLE_ADMIN', ...]
    const fromApiPermissions = Array.isArray(data.permissions) ? data.permissions : null; // ['CONTRACT_CREATE', ...]

    // Fallback decode JWT để lấy thêm thông tin
    const payload = this.safeDecodeJwt<any>(accessToken);

    // Roles: nếu BE không trả ra mảng object roles, suy ra từ authorities/roles trong JWT
    const roles =
      fromApiRoles
      ?? this.toRoleObjects(
           (payload?.roles as string[]) ||
           (payload?.authorities as string[]) ||
           []
         );

    const authorities =
      fromApiAuthorities
      ?? (payload?.authorities as string[])
      ?? [];

    const permissions =
      fromApiPermissions
      ?? (payload?.permissions as string[])
      ?? this.extractPermissionsFromAuthorities(authorities);

    localStorage.setItem('roles', JSON.stringify(roles));            // guard đang đọc 'roles'
    localStorage.setItem('authorities', JSON.stringify(authorities));
    localStorage.setItem('permissions', JSON.stringify(permissions));

    // option: lưu vài thông tin hiển thị
    if (payload?.sub) localStorage.setItem('userEmail', payload.sub);
    if (payload?.name) localStorage.setItem('fullName', payload.name);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  clearSession(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('roles');
    localStorage.removeItem('authorities');
    localStorage.removeItem('permissions');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('fullName');
  }

  // ========= PRIVATE UTILS =========

  /** Decode JWT payload an toàn (base64url) */
  private safeDecodeJwt<T = any>(jwt: string | undefined): T | null {
    if (!jwt) return null;
    const parts = jwt.split('.');
    if (parts.length !== 3) return null;
    try {
      const b64 = parts[1]
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(parts[1].length + (4 - (parts[1].length % 4)) % 4, '=');
      const json = atob(b64);
      return JSON.parse(json) as T;
    } catch {
      return null;
    }
  }

  /** Từ mảng chuỗi authorities/roles → mảng object { roleKey: 'ADMIN' } cho phù hợp PermissionGuard */
  private toRoleObjects(source: string[]): Array<{ roleKey: string }> {
    // Ưu tiên các chuỗi bắt đầu "ROLE_"
    const roleNames = source
      .filter(s => typeof s === 'string')
      .map(s => s.startsWith('ROLE_') ? s.substring(5) : s)
      .filter(s => !!s);

    // unique
    const uniq = Array.from(new Set(roleNames));
    return uniq.map(r => ({ roleKey: r }));
  }

  /** Nếu không có permissions riêng, lấy từ authorities loại bỏ ROLE_ */
  private extractPermissionsFromAuthorities(authorities: string[]): string[] {
    if (!Array.isArray(authorities)) return [];
    const perms = authorities
      .filter(a => typeof a === 'string' && !a.startsWith('ROLE_'));
    return Array.from(new Set(perms));
  }
}
