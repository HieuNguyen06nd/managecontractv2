export interface VariableConfig {
  trueLabel?: string;
  falseLabel?: string;
  options?: string[];
  items?: string[];
  tableName?: string;
  columns?: Array<{ name: string; type: VariableType }>; // Sửa: type là VariableType
  rows?: number;
  min?: number;
  max?: number;
  placeholder?: string;
  defaultValue?: any;
  [key: string]: any;
}

export enum VariableType {
  TEXT = 'TEXT',
  NUMBER = 'NUMBER', 
  DATE = 'DATE',
  BOOLEAN = 'BOOLEAN',
  DROPDOWN = 'DROPDOWN',
  LIST = 'LIST',
  TABLE = 'TABLE',
  TEXTAREA = 'TEXTAREA'
}

export interface TemplateVariablePreview {
  varName: string;
  varType: VariableType; // Sử dụng enum
  orderIndex: number;
  sampleValue?: string;
  required?: boolean;
  name?: string;
  config?: VariableConfig;
  allowedValues?: string[];
}

export interface TemplatePreviewResponse {
  tempFileName: string;
  variables: TemplateVariablePreview[];
}

export interface TemplateVariableRequest {
  varName: string;
  varType: VariableType; // Sử dụng enum
  required: boolean;
  name: string;
  orderIndex: number;
  config?: VariableConfig;
  allowedValues?: string[];
}

export interface VariableUpdateRequest {
  name: string;
  varName: string;
  varType: VariableType; // Sử dụng enum
  required: boolean;
  orderIndex: number;
  config?: VariableConfig;
  allowedValues?: string[];
}