import { VariableType, TemplateVariableRequest } from './template-preview-response.model';

export interface ContractTemplateCreateRequest {
  tempFileName: string;
  name: string;
  description?: string;
  categoryId?: number;
  variables: TemplateVariableRequest[];
}

export interface TemplateVariableCreateRequest {
  varName: string;
  varType?: string | null;
  required?: boolean;
  orderIndex: number;
  name?: string;
}