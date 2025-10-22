import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';

import { RoleService } from '../../core/services/role.service';
import { PermissionService } from '../../core/services/permission.service';
import { RolePermissionService } from '../../core/services/role-permission.service';

import { RoleResponse, RoleRequest } from '../../core/models/role.model';
import { PermissionResponse, PermissionRequest } from '../../core/models/permission.model';
import { ResponseData } from '../../core/models/response-data.model';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-role-permission',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './role-permission.component.html',
  styleUrls: ['./role-permission.component.scss']
})
export class RolePermissionComponent implements OnInit {

  // Tabs
  activeTab: 'roles' | 'permissions' | 'matrix' = 'matrix';

  // Data
  roles: RoleResponse[] = [];
  filteredRoles: RoleResponse[] = [];
  permissions: PermissionResponse[] = [];
  filteredPermissions: PermissionResponse[] = [];

  // Search terms
  roleSearch = '';
  permissionSearch = '';

  // Modals & forms
  showRoleModal = false;
  showPermissionModal = false;
  isEditRole = false;
  isEditPermission = false;
  editingRoleId?: number | string;
  editingPermissionId?: number | string;

  roleForm!: FormGroup;
  permissionForm!: FormGroup;

  // === MATRIX STATE ===
  modules: string[] = [];
  selectedModule = ''; // '' = tất cả
  matrixPermissions: PermissionResponse[] = [];

  // roleKey (string|number) -> Set<permissionId(number)>
  roleAssignedMap = new Map<any, Set<number>>();

  // Bản gốc để hoàn tác/so sánh khi lưu
  private originalRoleAssignedMap = new Map<any, Set<number>>();
  dirty = false;

  // Pagination
  matrixRolePage = 1;
  matrixRolePageSize = 5;
  pageSizeOptions = [5, 10, 20, 50];

  public Math = Math;

  constructor(
    private fb: FormBuilder,
    private roleService: RoleService,
    private permissionService: PermissionService,
    private rolePermissionService: RolePermissionService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.initForms();
    this.loadRoles();
    this.loadPermissions();
  }

  // ---------- HELPERS ----------
  // tạo _uid cho role: id || roleId || role_id || roleKey
  private attachUid(r: any): any {
    const uid = r?.id ?? r?.roleId ?? r?.role_id ?? r?.roleKey;
    return { ...r, _uid: uid };
  }
  // key dùng cho Map/UI
  private roleKey(role: any): any {
    return role?._uid ?? role?.roleKey;
  }
  // id số để gọi API; nếu không phải số -> NaN
  private rid(role: any): number {
    const raw = role?._uid ?? role?.id ?? role?.roleId ?? role?.role_id;
    return raw == null ? NaN : Number(raw);
  }
  private pid(perm: PermissionResponse): number {
    const raw: any = (perm as any)?.id;
    return raw == null ? NaN : Number(raw);
  }
  private cloneMap(src: Map<any, Set<number>>): Map<any, Set<number>> {
    const out = new Map<any, Set<number>>();
    src.forEach((set, key) => out.set(key, new Set(set)));
    return out;
  }
  private markDirty() { this.dirty = true; }

  // ---------- FORMS ----------
  private initForms() {
    this.roleForm = this.fb.group({
      roleKey: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['']
    });

    this.permissionForm = this.fb.group({
      permissionKey: ['', [Validators.required, Validators.maxLength(150)]],
      module: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['']
    });
  }

  // ---------- LOAD DATA ----------
  loadRoles() {
    this.roleService.getAll().subscribe({
      next: (res: ResponseData<RoleResponse[]>) => {
        this.roles = (res.data || []).map(r => this.attachUid(r as any));
        this.filteredRoles = [...this.roles];
        this.buildRoleAssignedMap();
      },
      error: () => this.toastr.error('Không thể tải danh sách Role')
    });
  }

  loadPermissions() {
    this.permissionService.getAll().subscribe({
      next: (res: ResponseData<PermissionResponse[]>) => {
        this.permissions = res.data || [];
        this.filteredPermissions = [...this.permissions];
        
        this.modules = Array.from(
          new Set(this.permissions.map(p => p.module).filter((m): m is string => !!m))
        ).sort();

        this.recomputeMatrixPermissions();
      },
      error: () => this.toastr.error('Không thể tải danh sách Permission')
    });
  }

  // ---------- MATRIX FUNCTIONS ----------
  private recomputeMatrixPermissions() {
    this.matrixPermissions = this.selectedModule
      ? this.permissions.filter(p => p.module === this.selectedModule)
      : [...this.permissions];
    
    this.matrixRolePage = 1;
  }

  onModuleChange() {
    this.recomputeMatrixPermissions();
  }

  private getPermIdsInScope(): number[] {
    const list = this.selectedModule
      ? this.getPermissionsByModule(this.selectedModule)
      : (this.permissions || []);
    return list.map(p => this.pid(p)).filter(Number.isFinite) as number[];
  }

private buildRoleAssignedMap() {
  this.roleAssignedMap.clear();

  // Tạo mảng role an toàn để gọi API + giữ _uid làm key cho Map/UI
  const safeRoles: any[] = (this.roles || []).map(r => {
    const uid = this.roleKey(r as any);        // key dùng cho Map/UI
    const nid = Number(this.rid(r as any));    // id số để gọi API
    return { ...(r as any), _uid: uid, __nid: nid };
  });

  // Chỉ gọi chi tiết cho role có id số hợp lệ
  const roleDetailRequests = safeRoles
    .filter(r => Number.isFinite(r.__nid))
    .map(r => this.roleService.getById(r.__nid));

  if (roleDetailRequests.length === 0) {
    // Không có role nào có id số: vẫn tạo entry rỗng để UI hiện checkbox
    (this.roles || []).forEach(r => {
      const key = this.roleKey(r as any);
      this.roleAssignedMap.set(key, new Set<number>());
    });
    this.originalRoleAssignedMap = this.cloneMap(this.roleAssignedMap);
    this.dirty = false;
    return;
  }

  forkJoin(roleDetailRequests).subscribe({
    next: (responses) => {
      responses.forEach((res, index) => {
        const role = safeRoles[index]; // any -> có _uid
        const perms = res.data?.permissions || [];
        this.roleAssignedMap.set(role._uid, new Set(perms.map((p: any) => Number(p.id))));
      });

      // đảm bảo mọi role đều có entry trong Map
      (this.roles || []).forEach(r => {
        const key = this.roleKey(r as any);
        if (!this.roleAssignedMap.has(key)) {
          this.roleAssignedMap.set(key, new Set<number>());
        }
      });

      this.originalRoleAssignedMap = this.cloneMap(this.roleAssignedMap);
      this.dirty = false;
    },
    error: () => this.toastr.error('Không thể tải chi tiết permissions của role')
  });
}
  

  isRoleAssigned(role: RoleResponse, perm: PermissionResponse): boolean {
    const key = this.roleKey(role as any);
    const pid = this.pid(perm);
    if (!Number.isFinite(pid)) return false;
    return this.roleAssignedMap.get(key)?.has(pid) ?? false;
  }

  toggleAllForRole(role: RoleResponse, checked: boolean) {
    if (!this.selectedModule) return;

    const key = this.roleKey(role as any);
    const permIds = this.getPermissionsByModule(this.selectedModule)
      .map(p => this.pid(p)).filter(Number.isFinite) as number[];

    const oldSet = this.roleAssignedMap.get(key) ?? new Set<number>();
    const newSet = new Set(oldSet);
    permIds.forEach(id => checked ? newSet.add(id) : newSet.delete(id));
    this.roleAssignedMap.set(key, newSet);

    this.markDirty();
  }

  isAllCheckedForRole(role: RoleResponse): boolean {
    if (!this.selectedModule) return false;
    const key = this.roleKey(role as any);
    const set = this.roleAssignedMap.get(key) ?? new Set<number>();
    const permIds = this.getPermissionsByModule(this.selectedModule)
      .map(p => this.pid(p)).filter(Number.isFinite) as number[];
    return permIds.length > 0 && permIds.every(id => set.has(id));
  }

  trackByModule(index: number, module: string) { return module; }
  trackByPermission(index: number, permission: PermissionResponse) {
    return (permission as any).id ?? permission.permissionKey ?? index;
  }
  trackByRole(index: number, role: any) {
    return role?._uid ?? role?.roleKey ?? index;
  }

  onToggleAssignment(role: RoleResponse, perm: PermissionResponse, checked: boolean) {
    const key = this.roleKey(role as any);
    const pid = this.pid(perm);
    if (!Number.isFinite(pid)) {
      this.toastr.error('Permission ID không hợp lệ');
      return;
    }

    const newMap = new Map(this.roleAssignedMap);
    const currentSet = newMap.get(key) || new Set<number>();
    const newSet = new Set(currentSet);

    if (checked) newSet.add(pid);
    else newSet.delete(pid);

    newMap.set(key, newSet);
    this.roleAssignedMap = newMap;

    this.markDirty();
  }

  getPermissionsByModule(module: string): PermissionResponse[] {
    return (this.permissions || []).filter(p => p.module === module);
  }

  selectAllPermissionsForModule() {
    if (!this.matrixPermissions.length) return;

    const roleKeys = this.pagedMatrixRoles.map(r => this.roleKey(r as any));
    const permIds = this.matrixPermissions.map(p => this.pid(p)).filter(Number.isFinite) as number[];

    roleKeys.forEach(roleKey => {
      const set = new Set(this.roleAssignedMap.get(roleKey) ?? new Set<number>());
      permIds.forEach(permId => set.add(permId));
      this.roleAssignedMap.set(roleKey, set);
    });

    this.markDirty();
  }

  deselectAllPermissionsForModule() {
    if (!this.matrixPermissions.length) return;

    const roleKeys = this.pagedMatrixRoles.map(r => this.roleKey(r as any));
    const permIds = this.matrixPermissions.map(p => this.pid(p)).filter(Number.isFinite) as number[];

    roleKeys.forEach(roleKey => {
      const set = new Set(this.roleAssignedMap.get(roleKey) ?? new Set<number>());
      permIds.forEach(permId => set.delete(permId));
      this.roleAssignedMap.set(roleKey, set);
    });

    this.markDirty();
  }

  saveMatrixChanges() {
    const scopedPermIds = new Set(this.getPermIdsInScope());
    if (scopedPermIds.size === 0) {
      this.toastr.info('Không có thay đổi trong phạm vi hiện tại');
      return;
    }

    const requests: any[] = [];

    for (const role of this.roles) {
      const key = this.roleKey(role as any);
      const rid = this.rid(role as any);
      if (!Number.isFinite(rid)) {
        // Không có ID số => không thể gọi API cho role này
        continue;
      }

      const before = this.originalRoleAssignedMap.get(key) ?? new Set<number>();
      const after  = this.roleAssignedMap.get(key) ?? new Set<number>();

      const added: number[] = [];
      after.forEach(pid => {
        if (!before.has(pid) && scopedPermIds.has(pid)) added.push(pid);
      });

      const removed: number[] = [];
      before.forEach(pid => {
        if (!after.has(pid) && scopedPermIds.has(pid)) removed.push(pid);
      });

      if (added.length)  requests.push(this.rolePermissionService.addPermissions(rid, added));
      if (removed.length) requests.push(this.rolePermissionService.removePermissions(rid, removed));
    }

    if (requests.length === 0) {
      this.toastr.info('Không có thay đổi để lưu');
      this.dirty = false;
      return;
    }

    forkJoin(requests).subscribe({
      next: () => {
        this.toastr.success('Đã lưu thay đổi phân quyền');
        this.originalRoleAssignedMap = this.cloneMap(this.roleAssignedMap);
        this.dirty = false;
      },
      error: () => this.toastr.error('Lưu thay đổi thất bại, vui lòng thử lại')
    });
  }

  discardMatrixChanges() {
    this.roleAssignedMap = this.cloneMap(this.originalRoleAssignedMap);
    this.dirty = false;
    this.toastr.info('Đã hoàn tác thay đổi');
  }

  // ---------- PAGINATION ----------
  get matrixRoleTotalPages(): number {
    return Math.max(1, Math.ceil(this.roles.length / this.matrixRolePageSize));
  }
  get pagedMatrixRoles(): RoleResponse[] {
    const start = (this.matrixRolePage - 1) * this.matrixRolePageSize;
    return this.roles.slice(start, start + this.matrixRolePageSize);
  }
  get pageNumbers(): number[] {
    return Array.from({ length: this.matrixRoleTotalPages }, (_, i) => i + 1);
  }
  changeMatrixRolePage(page: number) {
    if (page < 1 || page > this.matrixRoleTotalPages) return;
    this.matrixRolePage = page;
  }
  onMatrixRolePageSizeChange() { this.matrixRolePage = 1; }

  // ---------- ROLE MANAGEMENT ----------
  applyRoleFilter() {
    const query = this.roleSearch.toLowerCase().trim();
    this.filteredRoles = query 
      ? this.roles.filter(r => 
          r.roleKey.toLowerCase().includes(query) || 
          (r.description || '').toLowerCase().includes(query)
        )
      : [...this.roles];
  }

  openRoleModal(role?: RoleResponse) {
    this.isEditRole = !!role;
    this.showRoleModal = true;
    this.editingRoleId = (role as any)?._uid ?? (role as any)?.id;
    this.roleForm.reset({
      roleKey: role?.roleKey || '',
      description: role?.description || ''
    });
  }
  closeRoleModal() {
    this.showRoleModal = false;
    this.isEditRole = false;
    this.editingRoleId = undefined;
  }
  saveRole() {
    if (this.roleForm.invalid) {
      this.roleForm.markAllAsTouched();
      return;
    }
    const payload: RoleRequest = this.roleForm.value;
    const request = this.isEditRole && this.editingRoleId != null
      ? this.roleService.update(Number(this.editingRoleId), payload)
      : this.roleService.create(payload);

    request.subscribe({
      next: () => {
        this.toastr.success(this.isEditRole ? 'Cập nhật role thành công' : 'Tạo role thành công');
        this.closeRoleModal();
        this.loadRoles();
      },
      error: () => this.toastr.error(this.isEditRole ? 'Cập nhật role thất bại' : 'Tạo role thất bại')
    });
  }
  deleteRole(role: RoleResponse) {
    if (!confirm(`Xóa role "${role.roleKey}"?`)) return;
    const rid = this.rid(role as any);
    if (!Number.isFinite(rid)) {
      this.toastr.error('Role ID không hợp lệ');
      return;
    }
    this.roleService.delete(rid).subscribe({
      next: () => {
        this.toastr.success('Xóa role thành công');
        this.loadRoles();
      },
      error: () => this.toastr.error('Xóa role thất bại')
    });
  }

  // ---------- PERMISSION MANAGEMENT ----------
  applyPermissionFilter() {
    const query = this.permissionSearch.toLowerCase().trim();
    this.filteredPermissions = query
      ? this.permissions.filter(p =>
          p.permissionKey.toLowerCase().includes(query) ||
          p.module.toLowerCase().includes(query) ||
          (p.description || '').toLowerCase().includes(query)
        )
      : [...this.permissions];
  }
  openPermissionModal(perm?: PermissionResponse) {
    this.isEditPermission = !!perm;
    this.showPermissionModal = true;
    this.editingPermissionId = (perm as any)?.id;
    this.permissionForm.reset({
      permissionKey: perm?.permissionKey || '',
      module: perm?.module || '',
      description: perm?.description || ''
    });
  }
  closePermissionModal() {
    this.showPermissionModal = false;
    this.isEditPermission = false;
    this.editingPermissionId = undefined;
  }
  savePermission() {
    if (this.permissionForm.invalid) {
      this.permissionForm.markAllAsTouched();
      return;
    }
    const payload: PermissionRequest = this.permissionForm.value;
    const request = this.isEditPermission && this.editingPermissionId != null
      ? this.permissionService.update(Number(this.editingPermissionId), payload)
      : this.permissionService.create(payload);

    request.subscribe({
      next: () => {
        this.toastr.success(this.isEditPermission ? 'Cập nhật permission thành công' : 'Tạo permission thành công');
        this.closePermissionModal();
        this.loadPermissions();
      },
      error: () => this.toastr.error(this.isEditPermission ? 'Cập nhật permission thất bại' : 'Tạo permission thất bại')
    });
  }
  deletePermission(perm: PermissionResponse) {
    if (!confirm(`Xóa permission "${perm.permissionKey}"?`)) return;
    const pid = this.pid(perm);
    if (!Number.isFinite(pid)) {
      this.toastr.error('Permission ID không hợp lệ');
      return;
    }
    this.permissionService.delete(pid).subscribe({
      next: () => {
        this.toastr.success('Xóa permission thành công');
        this.loadPermissions();
      },
      error: () => this.toastr.error('Xóa permission thất bại')
    });
  }

  // ---------- UTILITIES ----------
  trackById(index: number, item: any) {
    return item?._uid ?? item?.id ?? index;
  }
  setTab(tab: 'roles' | 'permissions' | 'matrix') {
    this.activeTab = tab;
  }
}
