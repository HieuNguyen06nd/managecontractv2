import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ResponseData } from '../models/response-data.model';

@Injectable({ providedIn: 'root' })
export class RolePermissionService {
  private baseUrl = `${environment.apiUrl}/role-permissions`;

  constructor(private http: HttpClient) {}

  /**
   * Gán mới quyền cho role (reset toàn bộ quyền trước đó)
   * POST /api/role-permissions/assign?roleId=...
   * body: number[] permissionIds
   */
  assignPermissions(roleId: number, permissionIds: number[]): Observable<ResponseData<string>> {
    const params = new HttpParams().set('roleId', roleId.toString());
    return this.http.post<ResponseData<string>>(`${this.baseUrl}/assign`, permissionIds, { params });
  }

  /**
   * Thêm quyền vào role (không xoá quyền cũ)
   * POST /api/role-permissions/add?roleId=...
   * body: number[] permissionIds
   */
  addPermissions(roleId: number, permissionIds: number[]): Observable<ResponseData<string>> {
    const params = new HttpParams().set('roleId', roleId.toString());
    return this.http.post<ResponseData<string>>(`${this.baseUrl}/add`, permissionIds, { params });
  }

  /**
   * Xoá quyền cụ thể khỏi role
   * DELETE /api/role-permissions/remove?roleId=...
   * body: number[] permissionIds
   * Lưu ý: Angular DELETE có thể gửi body qua options.body
   */
  removePermissions(roleId: number, permissionIds: number[]): Observable<ResponseData<string>> {
    const params = new HttpParams().set('roleId', roleId.toString());
    return this.http.request<ResponseData<string>>('DELETE', `${this.baseUrl}/remove`, {
      params,
      body: permissionIds,
    });
  }
}
