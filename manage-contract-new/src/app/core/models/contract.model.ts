export interface CreateContractRequest {
  templateId: number;
  title: string;
  variables: VariableValueRequest[];
  flowId?: number| null;         // optional
  allowChangeFlow?: boolean;
}

export interface ContractResponse {
  id: number;
  contractNumber?: string;
  title?: string;
  status?: string;
  filePath?: string;       // nhớ để string | undefined (không dùng null)
  templateId?: number;     // nếu bạn có field này ở BE
  templateName?: string;
  createdAt?: string;

  currentStepId?: number;
  currentStepName?: string;
  currentStepAction?: string;
  currentStepSignaturePlaceholder?: string;

  // ==== thêm để hiển thị luồng ký / preview ====
  hasFlow?: boolean;                 // BE set true nếu đã snapshot vào ContractApproval
  flowSource?: 'CONTRACT' | 'TEMPLATE_DEFAULT' | 'SELECTED';
  flowId?: number;
  flowName?: string;
  steps?: ApprovalStepResponse[];    // <-- khai báo type ở dưới
}
export interface ApprovalStepResponse {
  id: number;
  stepOrder: number;
  required?: boolean;
  approverType?: 'USER' | 'POSITION';
  isFinalStep?: boolean;

  employeeId?: number;
  employeeName?: string;
  positionId?: number;
  positionName?: string;
  departmentId?: number;
  departmentName?: string;

  action?: 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';
  signaturePlaceholder?: string;

  // runtime khi đã submit:
  status?: 'PENDING' | 'APPROVED' | 'REJECTED';
  isCurrent?: boolean;
  decidedBy?: string;
  decidedAt?: string;
}

export interface VariableValueRequest {
  varName: string;
  varValue: string;
}
export interface VariableValueResponse {
  varName: string;
  varValue: string;
}