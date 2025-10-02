export interface CreateContractRequest {
  templateId: number;
  title: string;
  variables: VariableValueRequest[];
  flowId?: number| null;         // optional
  allowChangeFlow?: boolean;
}

export interface ContractResponse {
  id: number;
  contractNumber: string;
  title: string;
  status: string;
  filePath?: string;  // optional
  templateName: string;
  variables: VariableValueResponse[];
}

export interface VariableValueRequest {
  varName: string;
  varValue: string;
}
export interface VariableValueResponse {
  varName: string;
  varValue: string;
}