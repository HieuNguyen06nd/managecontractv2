// contract-template.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http'; 
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';
import { ApprovalFlowResponse } from './contract-flow.service';

import { TemplatePreviewResponse } from '../models/template-preview-response.model';
import { ContractTemplateResponse } from '../models/contract-template-response.model';
import { VariableUpdateRequest } from '../models/variable-update-request.model';
import { ContractTemplateUpdateRequest } from '../models/contract-template-update-request.model';
import { ContractTemplateCreateRequest } from '../models/ontract-template-create-request.model';

export enum Status {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
  LOCKED = 'LOCKED',
  PENDING = 'PENDING'
}

export interface AuthAccountResponse {
  id: number;
  fullName?: string;
  email?: string;
  phone?: string;
}

export interface TemplateVariable {
  id: number;
  varName: string;
  varType: string | null;   
  required: boolean;
  orderIndex: number;
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

  updateTemplateStatus(
    templateId: number,
    body: { status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING' }
  ): Observable<ContractTemplateResponse> {
    return this.http
      .put<ResponseData<ContractTemplateResponse>>(
        `${this.baseUrl}/${templateId}/status`,
        body
      )
      .pipe(map((res) => this.normalizeOne(res.data)));
  }

  getDefaultFlowByTemplate(templateId: number): Observable<ApprovalFlowResponse> {
    return this.http
      .get<ResponseData<ApprovalFlowResponse>>(`${this.baseUrl}/${templateId}/default-flow`)
      .pipe(map((res) => res.data));
  }

  toggleTemplateStatus(templateId: number) {
    return this.http
      .patch<ResponseData<ContractTemplateResponse>>(
        `${this.baseUrl}/${templateId}/status/toggle`,
        {}
      )
      .pipe(map(res => this.normalizeOne(res.data)));
  }

  getAllTemplatesByStatus(status: Status) {
    return this.http
      .get<ResponseData<ContractTemplateResponse[]>>(
        `${this.baseUrl}/status/${status}`
      )
      .pipe(map(res => this.normalizeList(res.data)));
  }

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
      return {
        ...t,
        status: (t?.status ? String(t.status).toUpperCase() : 'INACTIVE') as any,
      };
    }

  /** Chuẩn hoá list template */
  private normalizeList(
    list: ContractTemplateResponse[] = []
  ): ContractTemplateResponse[] {
    return list.map((t) => this.normalizeOne(t));
  }
}
