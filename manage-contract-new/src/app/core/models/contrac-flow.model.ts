export interface ApprovalFlowRequest {
  name: string;
  description?: string;
  templateId: number;
  steps: ApprovalStepRequest[];
}

export interface ApprovalStepRequest {
  stepOrder: number;
  required: boolean;
  isFinalStep: boolean;
  approverType: ApproverType;
  employeeId?: number;
  positionId?: number;
  departmentId?: number;
}

export interface ApprovalFlowResponse {
  id: number;
  name: string;
  description?: string;
  templateId: number;
  steps: ApprovalStepResponse[];
}

export interface ApprovalStepResponse {
  id: number;
  stepOrder: number;
  required: boolean;
  approverType: ApproverType;
  isFinalStep: boolean;

  employeeId?: number;
  employeeName?: string;

  positionId?: number;
  positionName?: string;

  departmentId?: number;
  departmentName?: string;
}

export enum ApproverType {
  USER = 'USER',
  POSITION = 'POSITION'
}
export interface ApprovalStep {
  id?: number;
  approverType: ApproverType;
  departmentId?: number;
  employeeId?: number;
  positionId?: number;
  flowId?: number;
  isFinalStep: boolean;
  required: boolean;
  stepOrder: number;
}


export interface ApprovalFlow {
  id?: number;
  name: string;
  description?: string;
  templateId: number;
  steps: ApprovalStep[];
}

export type FlowSource = 'CONTRACT' | 'TEMPLATE_DEFAULT' | 'SELECTED';

export interface PlannedFlowResponse {
  flowSource: FlowSource;
  flowId?: number | null;
  flowName?: string | null;
  steps: ApprovalStepResponse[];
}