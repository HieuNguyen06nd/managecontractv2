export interface TemplateVariablePreview {
  varName: string;
  varType: string;
  orderIndex: number;
  sampleValue?: string; 
}

export interface TemplatePreviewResponse {
  tempFileName: string;
  variables: TemplateVariablePreview[];
}