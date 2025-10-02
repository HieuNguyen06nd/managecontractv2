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
  editingRoleId?: number;
  editingPermissionId?: number;

  roleForm!: FormGroup;
  permissionForm!: FormGroup;

  // === MATRIX STATE ===
  modules: string[] = [];
  selectedModule = ''; // '' = tất cả
  matrixPermissions: PermissionResponse[] = [];

  // roleId -> Set<permissionId> để check nhanh
  roleAssignedMap = new Map<number, Set<number>>();

  // Pagination
  matrixRolePage = 1;
  matrixRolePageSize = 5;
  pageSizeOptions = [5, 10, 20, 50];

  public Math = Math;

  private rid(role: RoleResponse)  { return Number((role as any).id); }
private pid(perm: PermissionResponse) { return Number((perm as any).id); }



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
        this.roles = res.data || [];
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
        
        // Tập module duy nhất
        this.modules = Array.from(
          new Set(this.permissions.map(p => p.module).filter(Boolean))
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

  private buildRoleAssignedMap() {
    this.roleAssignedMap.clear();
    
    // Load permissions for each role
    const roleDetailRequests = this.roles.map(role => 
      this.roleService.getById(role.id)
    );

    forkJoin(roleDetailRequests).subscribe({
      next: (responses) => {
        responses.forEach((res, index) => {
          const role = this.roles[index];
          const permissions = res.data?.permissions || [];
          this.roleAssignedMap.set(role.id, new Set(permissions.map((p: any) => p.id)));
        });
      },
      error: () => this.toastr.error('Không thể tải chi tiết permissions của role')
    });
  }
  isRoleAssigned(role: RoleResponse, perm: PermissionResponse): boolean {
    const rid = this.rid(role);
    const pid = this.pid(perm);
    return this.roleAssignedMap.get(rid)?.has(pid) ?? false;
  }

  toggleAllForRole(role: RoleResponse, checked: boolean) {
  if (!this.selectedModule) return;
  const rid = this.rid(role);
  const permIds = this.getPermissionsByModule(this.selectedModule).map(p => this.pid(p));

  const oldSet = this.roleAssignedMap.get(rid) ?? new Set<number>();
  const newSet = new Set(oldSet);
  permIds.forEach(id => checked ? newSet.add(id) : newSet.delete(id));
  this.roleAssignedMap.set(rid, newSet);

  const req$ = checked
    ? this.rolePermissionService.addPermissions(rid, permIds)
    : this.rolePermissionService.removePermissions(rid, permIds);

  req$.subscribe({
    error: () => {
      this.toastr.error('Thao tác chọn/bỏ theo cột thất bại');
      this.roleAssignedMap.set(rid, oldSet); // rollback
    }
  });
}

  isAllCheckedForRole(role: RoleResponse): boolean {
  if (!this.selectedModule) return false;
  const rid = this.rid(role);
  const set = this.roleAssignedMap.get(rid) ?? new Set<number>();
  const permIds = this.getPermissionsByModule(this.selectedModule).map(p => this.pid(p));
  return permIds.length > 0 && permIds.every(id => set.has(id));
}

  trackByModule(index: number, module: string): string {
    return module;
  }

  trackByPermission(index: number, permission: PermissionResponse): number {
    return permission.id;
  }

  trackByRole(index: number, role: RoleResponse): number {
    return role.id;
  }


onToggleAssignment(role: RoleResponse, perm: PermissionResponse, checked: boolean) {
  console.log('Toggle assignment:', role.id, role.roleKey, perm.id, perm.permissionKey, checked);
  
  // Tạo một bản sao mới của Map để trigger change detection
  const newMap = new Map(this.roleAssignedMap);
  const currentSet = newMap.get(role.id) || new Set<number>();
  const newSet = new Set(currentSet);
  
  if (checked) {
    newSet.add(perm.id);
  } else {
    newSet.delete(perm.id);
  }
  
  newMap.set(role.id, newSet);
  this.roleAssignedMap = newMap; // Gán lại để trigger change detection
  
  // Gọi API
  if (checked) {
    this.rolePermissionService.addPermissions(role.id, [perm.id]).subscribe({
      next: () => {
        this.toastr.success(`Đã gán ${perm.permissionKey} cho ${role.roleKey}`);
      },
      error: (error) => {
        console.error('Error assigning permission:', error);
        this.toastr.error('Gán permission thất bại');
        // Rollback
        const rollbackSet = new Set(currentSet);
        const rollbackMap = new Map(this.roleAssignedMap);
        rollbackMap.set(role.id, rollbackSet);
        this.roleAssignedMap = rollbackMap;
      }
    });
  } else {
    this.rolePermissionService.removePermissions(role.id, [perm.id]).subscribe({
      next: () => {
        this.toastr.success(`Đã gỡ ${perm.permissionKey} khỏi ${role.roleKey}`);
      },
      error: (error) => {
        console.error('Error removing permission:', error);
        this.toastr.error('Gỡ permission thất bại');
        // Rollback
        const rollbackSet = new Set(currentSet);
        const rollbackMap = new Map(this.roleAssignedMap);
        rollbackMap.set(role.id, rollbackSet);
        this.roleAssignedMap = rollbackMap;
      }
    });
  }
}

  getPermissionsByModule(module: string): PermissionResponse[] {
    return (this.permissions || []).filter(p => p.module === module);
  }


  // Bulk operations for matrix
  selectAllPermissionsForModule() {
    if (!this.matrixPermissions.length) return;

    const roleIds = this.pagedMatrixRoles.map(r => r.id);
    const permIds = this.matrixPermissions.map(p => p.id);

    roleIds.forEach(roleId => {
      const set = this.roleAssignedMap.get(roleId) ?? new Set<number>();
      permIds.forEach(permId => set.add(permId));
      this.roleAssignedMap.set(roleId, set);
    });

    this.bulkAssignPermissions(roleIds, permIds, true);
  }

  deselectAllPermissionsForModule() {
    if (!this.matrixPermissions.length) return;

    const roleIds = this.pagedMatrixRoles.map(r => r.id);
    const permIds = this.matrixPermissions.map(p => p.id);

    roleIds.forEach(roleId => {
      const set = this.roleAssignedMap.get(roleId) ?? new Set<number>();
      permIds.forEach(permId => set.delete(permId));
      this.roleAssignedMap.set(roleId, set);
    });

    this.bulkAssignPermissions(roleIds, permIds, false);
  }

  private bulkAssignPermissions(roleIds: number[], permIds: number[], assign: boolean) {
    const requests = roleIds.map(roleId => 
      assign 
        ? this.rolePermissionService.addPermissions(roleId, permIds)
        : this.rolePermissionService.removePermissions(roleId, permIds)
    );

    forkJoin(requests).subscribe({
      next: () => this.toastr.success(`Đã ${assign ? 'gán' : 'gỡ'} permissions hàng loạt`),
      error: () => this.toastr.error(`${assign ? 'Gán' : 'Gỡ'} permissions hàng loạt thất bại`)
    });
  }

  // ---------- PAGINATION ----------
  get matrixRoleTotalPages(): number {
    return Math.max(1, Math.ceil(this.roles.length / this.matrixRolePageSize));
  }

  get pagedMatrixRoles(): RoleResponse[] {
    const start = (this.matrixRolePage - 1) * this.matrixRolePageSize;
    return this.roles.slice(start, start + this.matrixRolePageSize);
  }

  changeMatrixRolePage(page: number) {
    if (page < 1 || page > this.matrixRoleTotalPages) return;
    this.matrixRolePage = page;
  }

  onMatrixRolePageSizeChange() {
    this.matrixRolePage = 1;
  }

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
    this.editingRoleId = role?.id;
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
    const request = this.isEditRole && this.editingRoleId
      ? this.roleService.update(this.editingRoleId, payload)
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
    
    this.roleService.delete(role.id).subscribe({
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
    this.editingPermissionId = perm?.id;
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
    const request = this.isEditPermission && this.editingPermissionId
      ? this.permissionService.update(this.editingPermissionId, payload)
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
    
    this.permissionService.delete(perm.id).subscribe({
      next: () => {
        this.toastr.success('Xóa permission thành công');
        this.loadPermissions();
      },
      error: () => this.toastr.error('Xóa permission thất bại')
    });
  }

  // ---------- UTILITIES ----------
  trackById(index: number, item: any): number {
    return item.id;
  }

  setTab(tab: 'roles' | 'permissions' | 'matrix') {
    this.activeTab = tab;
  }
}