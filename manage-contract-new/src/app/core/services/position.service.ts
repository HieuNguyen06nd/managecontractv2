import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

export interface PositionRequest {
    name: string;
    description?: string;
    status: Status;
}

export interface PositionResponse {
    id: number;
    name: string;
    description?: string;
    status: Status;
}


export enum Status {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
}

@Injectable({
  providedIn: 'root'
})
export class PositionService {
  private baseUrl = `${environment.apiUrl}/positions`;

  constructor(private http: HttpClient) {}

  // Lấy tất cả position (không phân trang)
  getAllPositions(): Observable<ResponseData<PositionResponse[]>> {
    return this.http.get<ResponseData<PositionResponse[]>>(this.baseUrl);
  }

  // Lấy tất cả position (có phân trang)
  getAllPositionsPaging(page: number, size: number): Observable<ResponseData<any>> {
    return this.http.get<ResponseData<any>>(
      `${this.baseUrl}/positions?page=${page}&size=${size}`
    );
  }

  // Lấy position theo ID
  getPositionById(id: number): Observable<ResponseData<PositionResponse>> {
    return this.http.get<ResponseData<PositionResponse>>(`${this.baseUrl}/${id}`);
  }

  // Tạo mới position
  createPosition(request: PositionRequest): Observable<ResponseData<PositionResponse>> {
    return this.http.post<ResponseData<PositionResponse>>(this.baseUrl, request);
  }

  // Cập nhật position
  updatePosition(id: number, request: PositionRequest): Observable<ResponseData<PositionResponse>> {
    return this.http.put<ResponseData<PositionResponse>>(`${this.baseUrl}/${id}`, request);
  }

  // Xóa position
  deletePosition(id: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(`${this.baseUrl}/${id}`);
  }

  getPositionsByDepartment(departmentId: number): Observable<ResponseData<PositionResponse[]>> {
    return this.http.get<ResponseData<PositionResponse[]>>(`${this.baseUrl}/by-department/${departmentId}`);
  }
}
