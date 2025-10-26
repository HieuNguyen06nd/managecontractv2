import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';
import { AuthProfileResponse } from '../models/auth.model';
import { AdminUpdateUserRequest } from '../models/auth.model';

export interface LoginRequest {
  emailOrPhone: string;
  password?: string;
  otp?: string;
}

export interface AuthResponse {
  token: string;
  refreshToken: string;
  userId: number;
  roles: RoleResponse[];
}

export interface RoleResponse {
  id: number;
  roleKey: string;
  description: string;
  permissions: PermissionResponse[];
}

export interface PermissionResponse {
  id: number;
  permissionKey: string;
  description: string;
  module: string;
}

export interface RegisterRequest {
  email: string;
  phone: string;
  password: string;
  fullName: string;
  roles: string[];
}

export interface RegisterResponse {
  message: string;
  status: string;
}

export interface AdminCreateUserRequest {
  email: string;
  fullName?: string;
  phone?: string;
  roleKeys?: string[];          
  departmentId?: number | null;
  positionId?: number | null;
}
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private baseUrl = `${environment.apiUrl}/auth`;

  constructor(private http: HttpClient) {}

  createByAdmin(payload: AdminCreateUserRequest) {
    return this.http.post<ResponseData<string>>(
      `${this.baseUrl}/users`,
      payload
    );
  }

    // API để lấy thông tin profile của người dùng
  getMyProfile(): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.get<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me`);
  }

  /**
   * Lấy danh sách toàn bộ nhân viên
   */
  getAll(): Observable<ResponseData<AuthProfileResponse[]>> {
    return this.http.get<ResponseData<AuthProfileResponse[]>>(
      `${this.baseUrl}/all`
    );
  }

  /**
   * Lấy thông tin chi tiết 1 nhân viên theo ID
   * @param id 
   */
  getById(id: number): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.get<ResponseData<AuthProfileResponse>>(
      `${this.baseUrl}/${id}`
    );
  }

  /**
   * Tạo mới nhân viên
   * @param payload 
   */
  create(payload: AuthProfileResponse): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.post<ResponseData<AuthProfileResponse>>(
      `${this.baseUrl}/create`,
      payload
    );
  }

  /**
   * Cập nhật thông tin nhân viên
   * @param id 
   * @param payload 
   */
  update(id: number, payload: AuthProfileResponse): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.put<ResponseData<AuthProfileResponse>>(
      `${this.baseUrl}/update/${id}`,
      payload
    );
  }


  updateByAdmin(id: number, payload: AdminUpdateUserRequest): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.put<ResponseData<AuthProfileResponse>>(
      `${this.baseUrl}/auth/users/${id}`,
      payload
    );
  }

  /**
   * Xóa nhân viên
   * @param id 
   */
  delete(id: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(
      `${this.baseUrl}/delete/${id}`
    );
  }

  // API để upload ảnh avatar
  uploadAvatar(file: File): Observable<ResponseData<AuthProfileResponse>> {
    const form = new FormData();
    form.append('file', file); // Tên field "file" khớp với @RequestParam("file") trong controller
    return this.http.post<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me/avatar`, form);
  }

  // API để upload ảnh chữ ký
  uploadSignature(file: File): Observable<ResponseData<AuthProfileResponse>> {
    const form = new FormData();
    form.append('file', file); // Tên field "file" khớp với @RequestParam("file") trong controller
    return this.http.post<ResponseData<AuthProfileResponse>>(`${this.baseUrl}/me/signature`, form);
  }

  // API để upload chữ ký từ Base64
  uploadSignatureBase64(imageBase64: string): Observable<ResponseData<AuthProfileResponse>> {
    return this.http.post<ResponseData<AuthProfileResponse>>(
      `${this.baseUrl}/me/signature/base64`,
      { dataUrl: imageBase64 }
    );
  }
}
