import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { StepApprovalRequest, SignStepRequest } from '../models/contract-approval.models';
import { environment } from '../../../environments/environment';
import { ContractResponse } from '../models/contract.model';

@Injectable({ providedIn: 'root' })
export class ContractApprovalService {
  private readonly baseUrl = `${environment.apiUrl}/contracts/approvals`;

  constructor(private http: HttpClient) {}

  // ===== CÁC PHƯƠNG THỨC PHÊ DUYỆT =====

  /**
   * GET /api/contracts/approvals/my-pending - Hợp đồng chờ tôi xử lý
   */
  getMyPendingContracts(): Observable<ResponseData<ContractResponse[]>> {
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl}/my-pending`
    );
  }

  /**
   * GET /api/contracts/approvals/my-handled?status= - Hợp đồng tôi đã xử lý
   */
  getMyHandledContracts(status: string): Observable<ResponseData<ContractResponse[]>> {
    const params = new HttpParams().set('status', status);
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl}/my-handled`,
      { params }
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/steps/{stepId}/approve - Phê duyệt bước
   */
  approveStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/approve`,
      body
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/steps/{stepId}/reject - Từ chối bước
   */
  rejectStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/reject`,
      body
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/steps/{stepId}/sign - Ký bước
   */
  signStep(contractId: number, stepId: number, body: SignStepRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/sign`,
      body
    );
  }

  /**
   * GET /api/contracts/approvals/{contractId}/progress - Tiến trình phê duyệt
   */
  getApprovalProgress(contractId: number): Observable<ResponseData<ContractResponse>> {
    return this.http.get<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/progress`
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/submit - Gửi phê duyệt
   */
  submitForApproval(contractId: number, flowId?: number): Observable<ResponseData<ContractResponse>> {
    let params = new HttpParams();
    if (flowId) {
      params = params.set('flowId', flowId.toString());
    }
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/submit`,
      {},
      { params }
    );
  }

  /**
   * GET /api/contracts/approvals/{contractId}/preview - Preview approval flow
   */
  previewApprovalFlow(contractId: number, flowId?: number): Observable<ResponseData<ContractResponse>> {
    let params = new HttpParams();
    if (flowId) {
      params = params.set('flowId', flowId.toString());
    }
    return this.http.get<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/preview`,
      { params }
    );
  }
}