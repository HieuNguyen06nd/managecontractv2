import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ResponseData } from '../models/response-data.model';
import { environment } from '../../../environments/environment';

export interface DepartmentResponse {
  id: number;
  name: string;
  level: number;
  parentId?: number;
  parentName?: string;
  leaderId?: number;
  leaderName?: string;
  status:Status;
}

export interface DepartmentRequest {
  name: string;
  level?: number;
  parentId?: number;
  leaderId?: number;
  status?: Status;
  description?: string;
}

export interface Pageable {
  page: number;
  size: number;
  sort?: string; // Ví dụ: 'name,asc'
}

export enum Status {
    ACTIVE='ACTIVE',
    INACTIVE = 'INACTIVE',
    LOCKED = 'LOCKED',
}
@Injectable({
  providedIn: 'root'
})
export class DepartmentService {
  private baseUrl = `${environment.apiUrl}/departments`;

  constructor(private http: HttpClient) {}

  // Lấy tất cả departments
  getAllDepartments(): Observable<ResponseData<DepartmentResponse[]>> {
    return this.http.get<ResponseData<DepartmentResponse[]>>(this.baseUrl);
  }

  getAllDepartmentsPaged(pageable: Pageable): Observable<any> {
    let params = new HttpParams()
      .set('page', pageable.page.toString())
      .set('size', pageable.size.toString());

    if (pageable.sort) {
      params = params.set('sort', pageable.sort);
    }

    return this.http.get<any>(`${this.baseUrl}/paged`, { params });
  }

  // Lấy department theo id
  getDepartmentById(id: number): Observable<ResponseData<DepartmentResponse>> {
    return this.http.get<ResponseData<DepartmentResponse>>(`${this.baseUrl}/${id}`);
  }

  // Phương thức mới: Tạo phòng ban
  createDepartment(payload: DepartmentRequest): Observable<ResponseData<DepartmentResponse>> {
    return this.http.post<ResponseData<DepartmentResponse>>(this.baseUrl, payload);
  }

  // Phương thức mới: Cập nhật phòng ban
  updateDepartment(id: number, payload: DepartmentRequest): Observable<ResponseData<DepartmentResponse>> {
    return this.http.put<ResponseData<DepartmentResponse>>(`${this.baseUrl}/${id}`, payload);
  }

  // Phương thức mới: Xóa phòng ban
  deleteDepartment(id: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(`${this.baseUrl}/${id}`);
  }
}