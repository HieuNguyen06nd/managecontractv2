// contract-template.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';


import { TemplatePreviewResponse } from '../models/template-preview-response.model';
import { VariableUpdateRequest } from '../models/variable-update-request.model';
import { ContractTemplateUpdateRequest } from '../models/contract-template-update-request.model';
import { ContractTemplateCreateRequest } from '../models/ontract-template-create-request.model';
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
  variables: TemplateVariable[];

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


@Injectable({ providedIn: 'root' })
export class ContractTemplateService {
  private baseUrl = `${environment.apiUrl}/templates`;

  constructor(private http: HttpClient) {}

  // ------------------ PREVIEW ------------------
  previewFromFile(file: File): Observable<TemplatePreviewResponse> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http
      .post<ResponseData<TemplatePreviewResponse>>(
        `${this.baseUrl}/preview-file`,
        formData
      )
      .pipe(map((res) => res.data));
  }

  previewFromLink(docLink: string): Observable<TemplatePreviewResponse> {
    const formData = new FormData();
    formData.append('docLink', docLink);

    return this.http
      .post<ResponseData<TemplatePreviewResponse>>(
        `${this.baseUrl}/preview-link`,
        formData
      )
      .pipe(map((res) => res.data));
  }

  // ------------------ FINALIZE (CREATE) ------------------
  finalizeTemplate(
    request: ContractTemplateCreateRequest
  ): Observable<ContractTemplateResponse> {
    return this.http
      .post<ResponseData<ContractTemplateResponse>>(
        `${this.baseUrl}/finalize`,
        request
      )
      .pipe(map((res) => this.normalizeOne(res.data)));
  }

  // ------------------ UPDATE VARIABLES ------------------
  updateVariables(
    templateId: number,
    variables: VariableUpdateRequest[]
  ): Observable<void> {
    return this.http
      .post<ResponseData<void>>(
        `${this.baseUrl}/${templateId}/variables`,
        variables
      )
      .pipe(map((res) => res.data));
  }

  // ------------------ UPDATE TEMPLATE (name/description/...) ------------------
  updateTemplate(
    templateId: number,
    request: ContractTemplateUpdateRequest
  ): Observable<ContractTemplateResponse> {
    return this.http
      .put<ResponseData<ContractTemplateResponse>>(
        `${this.baseUrl}/${templateId}`,
        request
      )
      .pipe(map((res) => this.normalizeOne(res.data)));
  }

  // ------------------ GET ALL ------------------
  getAllTemplates(): Observable<ContractTemplateResponse[]> {
    return this.http
      .get<ResponseData<ContractTemplateResponse[]>>(this.baseUrl)
      .pipe(map((res) => this.normalizeList(res.data)));
  }

  // ------------------ (OPTIONAL) GET BY ID ------------------
  getTemplateById(id: number): Observable<ContractTemplateResponse> {
    return this.http
      .get<ResponseData<ContractTemplateResponse>>(`${this.baseUrl}/${id}`)
      .pipe(map((res) => this.normalizeOne(res.data)));
  }

  // =========================================================
  // =============== NORMALIZE HELPER METHODS ================
  // =========================================================

  /** Đảm bảo mọi TemplateVariable có định dạng đúng theo models (tránh undefined) */
  private normalizeVariable(v: any): TemplateVariable {
    const normalized: TemplateVariable = {
      id: v?.id ?? 0,
      varName: v?.varName ?? '',
      varType: v?.varType ?? null, // 👈 ép undefined -> null để khớp models
      required: !!v?.required,
      orderIndex: v?.orderIndex ?? 0,
    };
    return normalized;
  }

  /** Chuẩn hoá 1 template */
  private normalizeOne(t: ContractTemplateResponse): ContractTemplateResponse {
    if (!t) return t;
    return {
      ...t,
      variables: (t.variables ?? []).map((v) => this.normalizeVariable(v)),
    };
  }

  /** Chuẩn hoá list template */
  private normalizeList(
    list: ContractTemplateResponse[] = []
  ): ContractTemplateResponse[] {
    return list.map((t) => this.normalizeOne(t));
  }
}
