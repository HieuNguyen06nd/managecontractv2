// src/app/core/services/contract.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

import { ResponseData } from '../models/response-data.model';
import { ContractResponse } from '../models/contract.model';

export interface CreateContractRequest {
  templateId: number;
  title: string;
  variables: VariableValueRequest[];
  flowId?: number | null;
  allowChangeFlow?: boolean;
}
export interface VariableValueRequest { varName: string; varValue: string; }
export interface VariableValueResponse { varName: string; varValue: string; }

@Injectable({ providedIn: 'root' })
export class ContractService {
  /** Gốc API, chuẩn hóa bỏ dấu / thừa và thêm prefix /api/contracts */
  private readonly baseUrl = `${(environment.apiUrl || '').replace(/\/+$/, '')}/contracts`;

  constructor(private http: HttpClient) {}

  // ===== CRUD / Queries =====

  /** POST /api/contracts/create */
  createContract(request: CreateContractRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(`${this.baseUrl}/create`, request);
  }

  /** GET /api/contracts/{id} */
  getContractById(id: number): Observable<ResponseData<ContractResponse>> {
    return this.http.get<ResponseData<ContractResponse>>(`${this.baseUrl}/${id}`);
  }

  /** GET /api/contracts */
  getAllContracts(): Observable<ResponseData<ContractResponse[]>> {
    return this.http.get<ResponseData<ContractResponse[]>>(`${this.baseUrl}`);
  }

  /** GET /api/contracts/my?status=... */
  getMyContracts(status?: string): Observable<ResponseData<ContractResponse[]>> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    return this.http.get<ResponseData<ContractResponse[]>>(`${this.baseUrl}/my`, { params });
  }

  // ===== Preview (HTML) =====

  /** POST /api/contracts/preview — preview HTML khi chưa lưu */
  previewTemplate(previewData: CreateContractRequest): Observable<ResponseData<string>> {
    return this.http.post<ResponseData<string>>(`${this.baseUrl}/preview`, previewData);
  }

  /** GET /api/contracts/{id}/preview — preview HTML khi đã có contract */
  previewContract(id: number): Observable<ResponseData<string>> {
    return this.http.get<ResponseData<string>>(`${this.baseUrl}/${id}/preview`);
  }

  // ===== PDF Helpers =====

  /** URL nhúng PDF vào <iframe> (Chrome/Edge viewer tham số mặc định) */
  buildPdfViewUrl(contractId: number, viewerParams = '#toolbar=1&navpanes=1&zoom=page-width'): string {
    return `${this.baseUrl}/${contractId}/file${viewerParams || ''}`;
  }

  /** URL tải PDF trực tiếp */
  /** URL tải PDF trực tiếp */
  buildPdfDownloadUrl(contractId: number, cacheBust?: number): string {
    const base = `${this.baseUrl}/${contractId}/file/download`;
    return cacheBust ? `${base}?t=${cacheBust}` : base;
  }


  /** GET PDF dạng Blob (nếu bạn muốn tự tạo objectURL) */
  getContractPdfBlob(contractId: number, cacheBust?: number): Observable<Blob> {
    const url = `${this.baseUrl}/${contractId}/file${cacheBust ? `?t=${cacheBust}` : ''}`;
    return this.http.get(url, { responseType: 'blob' });
  }

  /** GET PDF tải về dạng Blob */
  downloadContractPdfBlob(contractId: number, cacheBust?: number): Observable<Blob> {
    const url = `${this.baseUrl}/${contractId}/file/download${cacheBust ? `?t=${cacheBust}` : ''}`;
    return this.http.get(url, { responseType: 'blob' });
  }
}
