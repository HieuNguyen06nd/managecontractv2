export interface PositionRequest {
    name: string;
    description?: string;
    status: Status;
    departmentId: number;
}

export interface PositionResponse {
    id: number;
    name: string;
    description?: string;
    status: Status;
    departmentId?: number;  
    departmentName?: string;
}


export enum Status {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
}