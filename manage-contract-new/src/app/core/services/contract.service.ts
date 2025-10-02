import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

export interface CreateContractRequest {
  templateId: number;
  title: string;
  variables: VariableValueRequest[];
  flowId?: number| null;         // optional
  allowChangeFlow?: boolean;
}

export interface ContractResponse {
  id: number;
  contractNumber: string;
  title: string;
  status: string;
  filePath?: string;  // optional
  templateName: string;
  variables: VariableValueResponse[];
}

export interface VariableValueRequest {
  varName: string;
  varValue: string;
}
export interface VariableValueResponse {
  varName: string;
  varValue: string;
}
@Injectable({
  providedIn: 'root'
})
export class ContractService {
    private baseUrl = `${environment.apiUrl}/contracts`;

  constructor(private http: HttpClient) {}

  createContract(request: CreateContractRequest): Observable<ResponseData<ContractResponse>> {
    return this.http.post<ResponseData<ContractResponse>>(`${this.baseUrl}/create`, request);
  }

  getContractById(id: number): Observable<ResponseData<ContractResponse>> {
    return this.http.get<ResponseData<ContractResponse>>(`${this.baseUrl}/${id}`);
  }

  getAllContracts(): Observable<ResponseData<ContractResponse[]>> {
    return this.http.get<ResponseData<ContractResponse[]>>(`${this.baseUrl}`);
  }

  // Preview khi chưa lưu (POST)
  previewTemplate(previewData: CreateContractRequest): Observable<ResponseData<string>> {
    return this.http.post<ResponseData<string>>(`${this.baseUrl}/preview`, previewData);
  }

  // Preview khi đã lưu (GET)
  previewContract(id: number): Observable<ResponseData<string>> {
    return this.http.get<ResponseData<string>>(`${this.baseUrl}/${id}/preview`);
  }
}
