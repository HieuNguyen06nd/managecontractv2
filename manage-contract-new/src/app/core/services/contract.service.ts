// src/app/core/services/contract.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { map } from 'rxjs/operators';
import { ResponseData } from '../models/response-data.model';
import { ContractResponse, VariableValueRequest, VariableValueResponse, CreateContractRequest } from '../models/contract.model';
import { PlannedFlowResponse } from '../models/contrac-flow.model';

function normalizeContract(c: ContractResponse): ContractResponse {
  const vs = (c as any).variables as VariableValueResponse[] | undefined;
  return { ...c, variableValues: c.variableValues ?? vs ?? [] };
}

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
  getContractById(id: number) {
    return this.http.get<ResponseData<ContractResponse>>(`${this.baseUrl}/${id}`)
      .pipe(map(res => ({ ...res, data: normalizeContract(res.data) })));
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
    return `${this.baseUrl}/${contractId}/view${viewerParams || ''}`;
  }

  /** URL tải PDF trực tiếp */
  buildPdfDownloadUrl(contractId: number, cacheBust?: number): string {
    const base = `${this.baseUrl}/${contractId}/download`;
    return cacheBust ? `${base}?t=${cacheBust}` : base;
  }

  /** GET PDF dạng Blob (nếu bạn muốn tự tạo objectURL) */
  getContractPdfBlob(contractId: number, cacheBust?: number): Observable<Blob> {
    const url = `${this.baseUrl}/${contractId}/view${cacheBust ? `?t=${cacheBust}` : ''}`;
    return this.http.get(url, { responseType: 'blob' });
  }

  /** GET PDF tải về dạng Blob */
  downloadContractPdfBlob(contractId: number, cacheBust?: number): Observable<Blob> {
    const url = `${this.baseUrl}/${contractId}/download${cacheBust ? `?t=${cacheBust}` : ''}`;
    return this.http.get(url, { responseType: 'blob' });
  }

  /** PUT /api/contracts/{id} */
  updateContract(id: number, request: CreateContractRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.put<ResponseData<ContractResponse>>(`${this.baseUrl}/${id}`, request);
  }

  // ===== Change Approver =====

  /** PUT /api/contracts/{contractId}/approver/{stepId} */
  changeApprover(
    contractId: number, 
    stepId: number, 
    newApproverId: number, 
    isUserApprover: boolean
  ): Observable<ResponseData<string>> {
    const params = new HttpParams()
      .set('newApproverId', newApproverId.toString())
      .set('isUserApprover', isUserApprover.toString());
    return this.http.put<ResponseData<string>>(
      `${this.baseUrl}/${contractId}/approver/${stepId}`, 
      {}, 
      { params }
    );
  }

  /** PUT /api/contracts/{contractId}/cancel */
  cancelContract(contractId: number): Observable<ResponseData<void>> {
    return this.http.put<ResponseData<void>>(`${this.baseUrl}/${contractId}/cancel`, {});
  }

  // ===== Delete Contract =====

  /** DELETE /api/contracts/{contractId} */
  deleteContract(contractId: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(`${this.baseUrl}/${contractId}`);
  }

  previewContractPdf(payload: CreateContractRequest): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/preview-pdf`, payload, { responseType: 'blob' });
  }

  updateContractFlow(contractId: number, newFlowId: number, base: ContractResponse) {
    // Lấy variables hiện có để không bị mất khi update
    const srcVars: VariableValueResponse[] =
      (base.variableValues && base.variableValues.length ? base.variableValues :
      (base as any).variables) || [];

    const body: CreateContractRequest = {
      templateId: base.templateId!,                 // Backend đã bổ sung field này trong ContractResponse
      title: base.title || '',                      // giữ nguyên title hiện tại
      variables: srcVars.map(v => ({ varName: v.varName, varValue: v.varValue ?? '' })),
      flowId: newFlowId                              // CHỈ đổi flow
    };

    return this.http.put<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}`, body
    );
  }

  getPlannedFlow(contractId: number) {
    return this.http.get<ResponseData<PlannedFlowResponse>>(
      `${this.baseUrl}/${contractId}/planned-flow`
    );
  }

}