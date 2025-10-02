export interface PositionRequest {
    name: string;
    description?: string;
    status: Status;
}

export interface PositionResponse {
    id: number;
    name: string;
    description?: string;
    status: Status;
}


export enum Status {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
}