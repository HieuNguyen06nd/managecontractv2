
import { VariableConfig } from './template-preview-response.model';

export interface VariableUpdateRequest {
  name: string;
  varName: string;
  varType: string;
  required: boolean;
  orderIndex: number;
  config?: VariableConfig;
  allowedValues?: string[];
}
export interface TemplateVariableRequest {
  varName: string;
  varType: string;
  required: boolean;
  name: string;
  orderIndex: number;
  config?: { [key: string]: any };
  allowedValues?: string[];
}

export interface ContractTemplateCreateRequest {
  tempFileName: string;
  name: string;
  description?: string;
  categoryId?: number;
  variables: TemplateVariableRequest[];
}