import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { StepApprovalRequest, SignStepRequest } from '../models/contract-approval.models';
import { environment } from '../../../environments/environment';
import { ContractResponse } from '../models/contract.model';

@Injectable({ providedIn: 'root' })
export class ContractApprovalService {
  private readonly baseUrl = `${environment.apiUrl}/approval`;
  private readonly baseUrl2 = `${environment.apiUrl}/contracts`;

  constructor(private http: HttpClient) {}

  // ===== CÁC PHƯƠNG THỨC PHÊ DUYỆT =====

  /**
   * GET /api/approval/contracts/my-pending - Hợp đồng chờ tôi xử lý
   */
  getMyPendingContracts(): Observable<ResponseData<ContractResponse[]>> {
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl2}/approvals/my-pending`
    );
  }

  /**
   * GET /api/approval/contracts/my-handled?status= - Hợp đồng tôi đã xử lý
   */
  getMyHandledContracts(status: string): Observable<ResponseData<ContractResponse[]>> {
    const params = new HttpParams().set('status', status);
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl2}/approvals/my-handled`,
      { params }
    );
  }

  /**
   * POST /api/approval/steps/{stepId}/approve - Phê duyệt bước
   */
  approveStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/steps/${stepId}/approve`,
      body
    );
  }

  /**
   * POST /api/approval/steps/{stepId}/reject - Từ chối bước
   */
  rejectStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/steps/${stepId}/reject`,
      body
    );
  }

  /**
   * POST /api/approval/contracts/{contractId}/steps/{stepId}/sign - Ký bước
   */
  signStep(contractId: number, stepId: number, body: SignStepRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/contracts/${contractId}/steps/${stepId}/sign`,
      body
    );
  }

  /**
   * GET /api/approval/contracts/{contractId}/progress - Tiến trình phê duyệt
   */
  getApprovalProgress(contractId: number): Observable<ResponseData<ContractResponse>> {
    return this.http.get<ResponseData<ContractResponse>>(
      `${this.baseUrl}/contracts/${contractId}/progress`
    );
  }

  /**
   * POST /api/approval/contracts/{contractId}/submit - Gửi phê duyệt
   */
  submitForApproval(contractId: number, flowId?: number): Observable<ResponseData<ContractResponse>> {
    let params = new HttpParams();
    if (flowId) {
      params = params.set('flowId', flowId.toString());
    }
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/contracts/${contractId}/submit`,
      {},
      { params }
    );
  }
}