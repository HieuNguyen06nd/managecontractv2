export interface VariableConfig {
  trueLabel?: string;
  falseLabel?: string;
  options?: string[];
  items?: string[];
  tableName?: string;
  columns?: Array<{ name: string; type: string }>;
  rows?: number;
  min?: number;
  max?: number;
  placeholder?: string;
  defaultValue?: any;
}

export interface TemplateVariablePreview {
  varName: string;
  varType: string;
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
  varType: string;
  required: boolean;
  name: string;
  orderIndex: number;
  config?: VariableConfig;
  allowedValues?: string[];
}