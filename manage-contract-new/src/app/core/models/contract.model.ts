export interface VariableValueRequest {
  varName: string;
  varValue: string;
}
export interface VariableValueResponse {
  varName: string;
  varValue: string;
}

export interface CreateContractRequest {
  templateId: number;
  title: string;
  variables: VariableValueRequest[];
  flowId?: number | null;
  allowChangeFlow?: boolean;
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

  // runtime
  status?: 'PENDING' | 'APPROVED' | 'REJECTED';
  isCurrent?: boolean;
  decidedBy?: string;
  decidedAt?: string;
}

export interface ContractResponse {
  id: number;
  contractNumber?: string;
  title?: string;
  status?: string;
  filePath?: string;          // ok để optional string
  templateId?: number;
  templateName?: string;
  createdAt?: string;

  currentStepId?: number | null;
  currentStepName?: string | null;
  currentStepAction?: string | null;
  currentStepSignaturePlaceholder?: string | null;

  hasFlow?: boolean | null;
  flowSource?: 'CONTRACT' | 'TEMPLATE_DEFAULT' | 'SELECTED' | null;
  flowId?: number | null;
  flowName?: string | null;
  steps?: ApprovalStepResponse[] | null;

  variables?: VariableValueResponse[];       
  variableValues?: VariableValueResponse[]; 
}
