import { TemplateVariable } from './contract-template-response.model';

export interface ContractTemplateCreateRequest {
  tempFileName: string;
  name?: string;
  description?: string;
  categoryId?: number | null;
  variables: TemplateVariableCreateRequest[];
}

export interface TemplateVariableCreateRequest {
  varName: string;
  varType?: string | null;
  required?: boolean;
  orderIndex: number;
  name?: string;
}