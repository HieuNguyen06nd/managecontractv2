export interface LoginRequest {
  emailOrPhone: string;
  password?: string;
  otp?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  userId: number;
  roles: RoleResponse[];
  requirePasswordChange: boolean;      
  changePasswordToken: string | null;
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
  avatarImage?: string;      // Cập nhật thành avatarImage thay vì signatureImage
  signatureImage?: string;   // Chữ ký
  department?: string;       // Phòng ban
  position?: string;         // Chức vụ
  status: StatusUser;        // Trạng thái người dùng
  roles: RoleResponse[];     // Danh sách vai trò người dùng
  lastLogin?: string;        // Ngày giờ đăng nhập cuối cùng, nếu có
  dateCreated: string;       // Ngày tạo tài khoản
  passwordChangedAt?: string; // Thời gian đổi mật khẩu (nếu có)
}

export enum StatusUser {
    ACTIVE = 'ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
    PENDING = 'PENDING'
}
