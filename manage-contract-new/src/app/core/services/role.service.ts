import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';
import { RoleRequest, RoleResponse } from '../models/role.model';

@Injectable({ providedIn: 'root' })
export class RoleService {
  private baseUrl = `${environment.apiUrl}/roles`;

  constructor(private http: HttpClient) {}

  create(payload: RoleRequest): Observable<ResponseData<RoleResponse>> {
    return this.http.post<ResponseData<RoleResponse>>(this.baseUrl, payload);
  }

  getAll(): Observable<ResponseData<RoleResponse[]>> {
    return this.http.get<ResponseData<RoleResponse[]>>(this.baseUrl);
  }

  getById(id: number): Observable<ResponseData<RoleResponse>> {
    return this.http.get<ResponseData<RoleResponse>>(`${this.baseUrl}/${id}`);
  }

  update(id: number, payload: RoleRequest): Observable<ResponseData<RoleResponse>> {
    return this.http.put<ResponseData<RoleResponse>>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<ResponseData<void>> {
    return this.http.delete<ResponseData<void>>(`${this.baseUrl}/${id}`);
  }
}
