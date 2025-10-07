import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

export enum ApproverType {
  USER = 'USER',
  POSITION = 'POSITION'
}

export enum ApprovalAction {
  APPROVE_ONLY = 'APPROVE_ONLY',
  SIGN_ONLY = 'SIGN_ONLY',
  SIGN_THEN_APPROVE = 'SIGN_THEN_APPROVE'
}

export interface ApprovalStepRequest {
  stepOrder: number;
  required: boolean;
  isFinalStep: boolean;
  approverType: ApproverType;
  action: ApprovalAction;
  placeholderKey?: string;
  employeeId?: number;     // bắt buộc nếu USER
  positionId?: number;     // bắt buộc nếu POSITION
  departmentId?: number;   // bắt buộc nếu POSITION
  
}

export interface ApprovalFlowRequest {
  name: string;
  description?: string;
  templateId: number;
  steps: ApprovalStepRequest[]; // dùng Array (List) cho thuận BE
}

export interface ApprovalStepResponse {
  id: number;
  stepOrder: number;
  required: boolean;
  approverType: ApproverType;
  isFinalStep: boolean;
  action: ApprovalAction;
  placeholderKey?: string;
  employeeId?: number;
  employeeName?: string;
  positionId?: number;
  positionName?: string;
  departmentId?: number;
  departmentName?: string;
  signaturePlaceholder?: string;
}

export interface ApprovalFlowResponse {
  id: number;
  name: string;
  description?: string;
  templateId: number;
  steps: ApprovalStepResponse[];
  isDefault: boolean;
}

@Injectable({ providedIn: 'root' })
export class ApprovalFlowService {
  private baseUrl = `${environment.apiUrl}/flows`;
  private tmplBase = `${environment.apiUrl}/templates`;

  constructor(private http: HttpClient) {}

  /** Tạo flow kèm toàn bộ steps */
  createFlow(req: ApprovalFlowRequest): Observable<ResponseData<ApprovalFlowResponse>> {
    return this.http.post<ResponseData<ApprovalFlowResponse>>(this.baseUrl, req);
  }

  /** Cập nhật flow (gửi lại toàn bộ steps sau khi thêm/sửa/xoá ở UI) */
  updateFlow(flowId: number, req: ApprovalFlowRequest): Observable<ResponseData<ApprovalFlowResponse>> {
    return this.http.put<ResponseData<ApprovalFlowResponse>>(`${this.baseUrl}/${flowId}`, req);
  }

  /** Lấy chi tiết flow */
  getFlowById(flowId: number): Observable<ResponseData<ApprovalFlowResponse>> {
    return this.http.get<ResponseData<ApprovalFlowResponse>>(`${this.baseUrl}/${flowId}`);
  }

  /** Liệt kê flows theo template (dùng cho bước 3 chọn flow) */
  listFlowsByTemplate(templateId: number): Observable<ResponseData<ApprovalFlowResponse[]>> {
    return this.http.get<ResponseData<ApprovalFlowResponse[]>>(`${this.baseUrl}/by-template/${templateId}`);
  }

  /** Đặt flow mặc định cho template */
  setDefaultFlow(templateId: number, flowId: number): Observable<ResponseData<void>> {
    return this.http.post<ResponseData<void>>(`${this.baseUrl}/by-template/${templateId}/${flowId}/set-default`, {});
  }

  /** Xoá flow */
  deleteFlow(flowId: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(`${this.baseUrl}/${flowId}`);
  }

  getDefaultFlowByTemplate(templateId: number) {
    return this.http.get<ResponseData<ApprovalFlowResponse>>(
      `${this.tmplBase}/${templateId}/default-flow`
    );
  }

}
