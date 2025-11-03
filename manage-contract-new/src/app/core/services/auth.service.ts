// src/app/core/services/auth.service.ts
import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { isPlatformBrowser } from '@angular/common';

import {
  LoginRequest,
  AuthResponse,
  RegisterRequest,
  RegisterResponse,
  AuthProfileResponse
} from '../models/auth.model';
import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

export interface Principal {
  sub?: string;
  roles: string[];        // ['ADMIN', 'EMPLOYEE', ...]
  permissions: string[];  // ['role.create', 'CREATE_ADMIN', ...]
  exp?: number;
  raw?: any;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private baseUrl = `${environment.apiUrl}/auth`;

  // Phát principal cho toàn app nếu bạn muốn lắng nghe thay đổi
  private principal$ = new BehaviorSubject<Principal | null>(null);

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    // Khởi tạo từ localStorage (F5 vẫn còn)
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('token');
      if (token) this.applyToken(token, /*silent*/ true);
    }
  }

  // ================== API gọi BE ==================
  login(req: LoginRequest): Observable<ResponseData<AuthResponse>> {
    return this.http.post<ResponseData<AuthResponse>>(`${this.baseUrl}/login`, req);
  }

  sendOtp(emailOrPhone: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/send-otp?email=${encodeURIComponent(emailOrPhone)}`,
      {}
    );
  }

  verifyOtp(email: string, otp: string): Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(
      `${this.baseUrl}/verify-otp?email=${encodeURIComponent(email)}&otp=${encodeURIComponent(otp)}`,
      {}
    );
  }

  resetPassword(data: { email: string; otp: string; newPassword: string; })
    : Observable<ResponseData<any>> {
    return this.http.post<ResponseData<any>>(`${this.baseUrl}/forgot-password/reset`, data);
  }

  register(req: RegisterRequest): Observable<ResponseData<RegisterResponse>> {
    return this.http.post<ResponseData<RegisterResponse>>(`${this.baseUrl}/register`, req);
  }

  getProfile(): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.get<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me`);
  }

  logout(email: string): Observable<ResponseData<string>> {
    return this.http.post<ResponseData<string>>(
      `${this.baseUrl}/logout?email=${encodeURIComponent(email)}`,
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

  // ================== SESSION HELPERS ==================

  /**
   * Gọi sau khi login thành công:
   * - Lưu refreshToken/userId
   * - Giải mã accessToken -> roles/permissions -> lưu localStorage
   * - Phát principal ra BehaviorSubject
   */
  setSessionFromResponse(res: ResponseData<AuthResponse>): void {
    const data = res?.data as any;
    if (!data) throw new Error('Phản hồi không hợp lệ');

    const accessToken: string | undefined = data.accessToken;
    const refreshToken: string | undefined = data.refreshToken;
    const userId: number | string | undefined = data.userId;

    if (!accessToken || !refreshToken) {
      throw new Error('Thiếu accessToken/refreshToken');
    }

    // refreshToken & userId
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('refreshToken', refreshToken);
      if (userId != null) localStorage.setItem('userId', String(userId));
    }

    // Giải mã & set principal + token
    this.applyToken(accessToken);
  }

  /**
   * Giải mã token → rút roles/permissions theo đúng payload bạn cung cấp:
   * {
   *   roles: [{ roleId, roleKey, ..., permissions: [{ id, permissionKey, ... }] }],
   *   sub, iat, exp
   * }
   */
  applyToken(accessToken: string, silent = false): void {
    const payload = this.safeDecodeJwt<any>(accessToken) || {};

    // Lấy danh sách roleKey
    const roles: string[] = Array.isArray(payload.roles)
      ? payload.roles
          .map((r: any) => r?.roleKey)
          .filter((x: any) => typeof x === 'string')
      : [];

    // Flatten permissionKey từ từng role
    const permFromRoles: string[] = Array.isArray(payload.roles)
      ? payload.roles.flatMap((r: any) =>
          Array.isArray(r?.permissions)
            ? r.permissions.map((p: any) => p?.permissionKey).filter((x: any) => typeof x === 'string')
            : []
        )
      : [];

    // Nếu BE có nhét thêm `permissions` ở root → gộp vào luôn
    const permRoot: string[] = Array.isArray(payload.permissions) ? payload.permissions : [];

    // Gộp + unique, giữ nguyên chữ hoa/thường đúng như BE (bạn muốn “không map”)
    const permissions = Array.from(new Set<string>([...permFromRoles, ...permRoot]));

    // Lưu localStorage (để guard cũ dùng) + lưu token
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('token', accessToken);
      localStorage.setItem('roles', JSON.stringify(roles.map(r => ({ roleKey: r })))); // dạng [{roleKey:'ADMIN'}]
      localStorage.setItem('permissions', JSON.stringify(permissions));
      if (payload?.sub) localStorage.setItem('userEmail', payload.sub);
      if (payload?.name) localStorage.setItem('fullName', payload.name);
    }

    // Phát principal cho toàn app
    const principal: Principal = {
      sub: payload?.sub,
      roles,
      permissions,
      exp: payload?.exp,
      raw: payload
    };
    this.principal$.next(principal);

    if (!silent) {
      // optional: console.log('Principal:', principal);
    }
  }

  getToken(): string | null {
    return isPlatformBrowser(this.platformId) ? localStorage.getItem('token') : null;
  }

  clearSession(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('roles');
    localStorage.removeItem('permissions');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('fullName');
    this.principal$.next(null);
  }

  // ================== CHECK HELPERS (tuỳ dùng) ==================
  principal() { return this.principal$.value; }
  principalChanges() { return this.principal$.asObservable(); }

  hasPermission(key: string): boolean {
    const perms = this.getPermissions();
    return perms.has(key);
  }
  hasAnyPermission(keys: string[]): boolean {
    const perms = this.getPermissions();
    return keys.some(k => perms.has(k));
  }
  hasAllPermissions(keys: string[]): boolean {
    const perms = this.getPermissions();
    return keys.every(k => perms.has(k));
  }

  private getPermissions(): Set<string> {
    if (!isPlatformBrowser(this.platformId)) return new Set();
    try {
      const arr = JSON.parse(localStorage.getItem('permissions') || '[]');
      return new Set(arr as string[]);
    } catch {
      return new Set();
    }
  }

  // ================== PRIVATE UTILS ==================
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
}
