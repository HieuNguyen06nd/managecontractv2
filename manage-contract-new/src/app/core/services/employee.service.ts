import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';

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

export interface AuthProfileResponse {
  id: number;
  fullName: string;
  phone: string;
  email: string;
  signatureImage?: string;
  department?: any;
  position?: string;
  status: StatusUser;
  roles: RoleResponse[];
}

export enum StatusUser {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
    PENDING= 'PENDING'
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

  /**
   * Xóa nhân viên
   * @param id 
   */
  delete(id: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(
      `${this.baseUrl}/delete/${id}`
    );
  }
}
