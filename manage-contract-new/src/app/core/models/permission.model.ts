export interface PermissionRequest {
  permissionKey: string;
  description: string;
  module: string;
}

export interface PermissionResponse {
  id: number;
  permissionKey: string;
  description: string;
  module: string;
}
