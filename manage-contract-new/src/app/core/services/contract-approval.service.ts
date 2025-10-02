import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { StepApprovalRequest, SignStepRequest, ContractResponse, ContractStatus } from '../models/contract-approval.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ContractApprovalService {
  private readonly baseUrl = `${environment.apiUrl}/contracts/approvals`;

  constructor(private http: HttpClient) {}

  /**
   * POST /api/contracts/approvals/{contractId}/submit?flowId=
   */
  submitForApproval(contractId: number, flowId?: number): Observable<ResponseData<ContractResponse>> {
    let params = new HttpParams();
    if (flowId != null) {
      params = params.set('flowId', String(flowId));
    }
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/submit`,
      {},
      { params }
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/steps/{stepId}/approve
   * Lưu ý: Controller không dùng contractId trong method signature ở service,
   * nhưng đường dẫn vẫn yêu cầu contractId -> cần truyền đúng.
   */
  approveStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/approve`,
      body
    );
  }

  /**
   * POST /api/contracts/approvals/{contractId}/steps/{stepId}/reject
   */
  rejectStep(contractId: number, stepId: number, body: StepApprovalRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/reject`,
      body
    );
  }

  /**
   * GET /api/contracts/approvals/{contractId}/progress
   */
  getApprovalProgress(contractId: number): Observable<ResponseData<ContractResponse>> {
    return this.http.get<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/progress`
    );
  }

  /**
   * GET /api/contracts/approvals/my-handled?status=
   */
  getMyHandledContracts(status: ContractStatus | string): Observable<ResponseData<ContractResponse[]>> {
    const params = new HttpParams().set('status', String(status));
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl}/my-handled`,
      { params }
    );
  }

  /**
   * GET /api/contracts/approvals/my-pending
   */
  getMyPendingContracts(): Observable<ResponseData<ContractResponse[]>> {
    return this.http.get<ResponseData<ContractResponse[]>>(
      `${this.baseUrl}/my-pending`
    );
  }

  /**
   * (TÙY CHỌN) Sign step — CHỈ dùng nếu backend có endpoint:
   * e.g. POST /api/contracts/approvals/{contractId}/steps/{stepId}/sign
   */
  signStep(contractId: number, stepId: number, body: SignStepRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(
      `${this.baseUrl}/${contractId}/steps/${stepId}/sign`,
      body
    );
  }
}
