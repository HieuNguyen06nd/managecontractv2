import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';
import { PermissionRequest, PermissionResponse } from '../models/permission.model';

@Injectable({ providedIn: 'root' })
export class PermissionService {
  private baseUrl = `${environment.apiUrl}/permissions`;

  constructor(private http: HttpClient) {}

  create(payload: PermissionRequest): Observable<ResponseData<PermissionResponse>> {
    return this.http.post<ResponseData<PermissionResponse>>(this.baseUrl, payload);
  }

  update(id: number, payload: PermissionRequest): Observable<ResponseData<PermissionResponse>> {
    return this.http.put<ResponseData<PermissionResponse>>(`${this.baseUrl}/${id}`, payload);
  }

  delete(id: number): Observable<ResponseData<string | null>> {
    return this.http.delete<ResponseData<string | null>>(`${this.baseUrl}/${id}`);
  }

  getById(id: number): Observable<ResponseData<PermissionResponse>> {
    return this.http.get<ResponseData<PermissionResponse>>(`${this.baseUrl}/${id}`);
  }

  getAll(): Observable<ResponseData<PermissionResponse[]>> {
    return this.http.get<ResponseData<PermissionResponse[]>>(this.baseUrl);
  }
}
