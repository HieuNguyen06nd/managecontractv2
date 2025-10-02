// Model cho API /api/contracts/approvals

export interface ResponseData<T> {
  code?: number;         // BE của bạn đôi khi dùng code
  status?: number;       // hoặc status
  message?: string;
  data: T;
}

export interface StepApprovalRequest {
  comment?: string;
}

export type ContractStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | string;

export interface ContractApprovalItem {
  id: number;
  stepOrder: number;
  required: boolean;
  isFinalStep: boolean;
  isCurrent: boolean;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | string;

  // Thông tin người/đối tượng phê duyệt (nếu BE có map sang DTO):
  approverId?: number | null;
  approverName?: string | null;

  // Thông tin step gốc (tùy BE trả)
  stepId?: number;
  approverType?: 'USER' | 'POSITION';
  employeeId?: number | null;
  employeeName?: string | null;
  positionId?: number | null;
  positionName?: string | null;
  departmentId?: number | null;
  departmentName?: string | null;

  approvedAt?: string | null;
  comment?: string | null;
}

export interface ContractResponse {
  id: number;
  title: string;
  contractNumber?: string;
  status: ContractStatus;
  filePath?: string | null;

  template?: {
    id: number;
    name?: string;
  } | null;

  approvals?: ContractApprovalItem[];
}
export interface SignStepRequest {
  imageBase64: string;        
  placeholder?: string | null; 
  page?: number | null;       
  x?: number | null;
  y?: number | null;
  width?: number | null;     
  height?: number | null;
  comment?: string | null;
}