import { PermissionResponse } from './permission.model';

export interface RoleRequest {
  roleKey: string;
  description?: string;
  permissionIds?: number[];
}

export interface RoleResponse {
  id: number;
  roleKey: string;
  description: string;
  permissions: PermissionResponse[];
}
