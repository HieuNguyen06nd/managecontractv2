export interface DepartmentResponse {
  id: number;
  name: string;
  level: number;
  parentId?: number;
  parentName?: string;
  leaderId?: number;
  leaderName?: string;
  status:Status;
}

export interface DepartmentRequest {
  name: string;
  level?: number;
  parentId?: number;
  leaderId?: number;
  status?: Status;
  description?: string;
}

export interface Pageable {
  page: number;
  size: number;
  sort?: string; // Ví dụ: 'name,asc'
}

export enum Status {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
}