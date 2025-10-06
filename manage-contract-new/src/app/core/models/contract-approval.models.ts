export interface VariableValueResponse {
  varName: string;
  varValue: string;
}

/** Nếu muốn quản lý trạng thái bằng enum */
export enum ContractStatus {
  DRAFT = 'DRAFT',
  PENDING_APPROVAL = 'PENDING_APPROVAL',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED'
}
export interface StepApprovalRequest {
  comment?: string;
}

/**
 * Backend có phương thức signStep(SignStepRequest) trong service,
 * nhưng controller bạn gửi chưa lộ endpoint sign.
 * Vẫn định nghĩa để dùng khi backend bổ sung endpoint.
 */
export interface SignStepRequest {
  imageBase64: string;
  comment?: string | null;
  placeholder?: string | null;
  page?: number;   // <= optional
  x?: number;      // <= optional
  y?: number;      // <= optional
  width?: number;  // <= optional
  height?: number; // <= optional
}
