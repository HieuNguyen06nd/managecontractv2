// Trạng thái dùng chung với BE (ACTIVE/INACTIVE/LOCKED)
export enum Status {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  LOCKED = 'LOCKED',
}

// Người tạo template (BE: AuthAccountResponse) – để optional vì có thể BE không trả đủ
export interface AuthAccountResponse {
  id: number;
  fullName?: string;
  email?: string;
  phone?: string;
  // bổ sung field khác nếu BE có
}

// Biến của template (BE: TemplateVariableResponse)
export interface TemplateVariable {
  id: number;
  varName: string;
  varType: string | null;     // BE trả string enum -> giữ nguyên
  required: boolean;
  orderIndex: number;
}

export interface ContractTemplateResponse {
  id: number;
  name: string;
  description?: string;
  filePath: string;

  createdBy?: AuthAccountResponse;
  variables?: TemplateVariable[];

  // flow info
  defaultFlowId?: number;
  defaultFlowName?: string;
  allowOverrideFlow?: boolean;

  // category info
  categoryId?: number;
  categoryCode?: string;  
  categoryName?: string;  
  categoryStatus?: Status; // ACTIVE/INACTIVE
}
