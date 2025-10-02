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
  department?: string;
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