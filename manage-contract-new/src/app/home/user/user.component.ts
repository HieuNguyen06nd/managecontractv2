// src/app/home/user/user.component.ts
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { ToastrService } from 'ngx-toastr';

import { EmployeeService, AdminCreateUserRequest } from '../../core/services/employee.service';
import { AuthProfileResponse, RoleResponse } from '../../core/models/auth.model';
import {
  DepartmentService,
  DepartmentResponse,                 
} from '../../core/services/department.service';

import {
  PositionService,
  Status as PosStatus
} from '../../core/services/position.service';
import { PositionResponse } from '../../core/models/position.model';

import { BehaviorSubject, debounceTime, distinctUntilChanged } from 'rxjs';
import { RoleService } from '../../core/services/role.service';

type RoleOption = { roleKey: string; description: string };
type PositionOption = { id: number; name: string };

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss'],
})
export class UserComponent implements OnInit {
  // ===== Data =====
  users: AuthProfileResponse[] = [];
  filteredUsers: AuthProfileResponse[] = [];

  roles: RoleOption[] = [];
  departments: DepartmentResponse[] = [];
  positions: PositionOption[] = [];                          // options cho dropdown chức danh
  private posCache = new Map<number, PositionOption[]>();    // cache positions theo dept

  private departmentsLoaded = false;                         // cờ đã load phòng ban

  // ===== Modal flags =====
  isAddModalOpen = false;
  isEditModalOpen = false;
  isDeleteModalOpen = false;
  isResetModalOpen = false;

  currentUserId: number | null = null;
  currentUserName = '';

  // ===== Forms =====
  addForm: FormGroup;
  editForm: FormGroup;

  addSubmitting = false;
  editSubmitting = false;

  // ===== Search & Filters =====
  searchTerms = new BehaviorSubject<string>('');
  filterStatusTerm: string = '';
  filterRoleTerm: string = '';

  constructor(
    private fb: FormBuilder,
    private employeeService: EmployeeService,
    private departmentService: DepartmentService,
    private roleService: RoleService,
    private positionService: PositionService,
    private toastr: ToastrService
  ) {
    this.addForm = this.fb.group({
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      role: ['', Validators.required],
      departmentId: [null],
      positionId: [null],
    });

    this.editForm = this.fb.group({
      id: [null],
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      roleKeys: new FormControl<string[]>([]),
      departmentId: [null],
      positionId: [null],
      status: [''],
    });
  }

  ngOnInit(): void {
    this.loadUsers();
    this.loadDropdowns();

    this.searchTerms.pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.filterUsers());

    // valueChanges chỉ dùng khi user đổi thủ công (không áp khi patchValue)
    this.addForm.get('departmentId')?.valueChanges
      .pipe(distinctUntilChanged())
      .subscribe((deptId: number | null) => {
        this.loadPositionsByDepartment(deptId);
        this.addForm.get('positionId')?.setValue(null, { emitEvent: false });
      });

    this.editForm.get('departmentId')?.valueChanges
      .pipe(distinctUntilChanged())
      .subscribe((deptId: number | null) => {
        this.loadPositionsByDepartment(deptId);
        this.editForm.get('positionId')?.setValue(null, { emitEvent: false });
      });
  }

  // ===== API loads =====
  loadUsers(): void {
    this.employeeService.getAll().subscribe({
      next: res => { this.users = res.data || []; this.filterUsers(); },
      error: _ => this.toastr.error('Không tải được danh sách người dùng', 'Lỗi')
    });
  }

  loadDropdowns(): void {
    this.roleService.getAll().subscribe({
      next: r => {
        const arr = r.data || [];
        this.roles = arr.map(x => ({ roleKey: x.roleKey, description: x.description }));
      },
      error: _ => this.toastr.warning('Không tải được vai trò', 'Cảnh báo')
    });

    this.departmentService.getAllDepartments().subscribe({
      next: r => {
        this.departments = r.data || [];
        this.posCache.clear();
        this.positions = [];
        this.departmentsLoaded = true;
      },
      error: _ => this.toastr.error('Không tải được phòng ban', 'Lỗi')
    });
  }

  /** Nạp positions theo department (có cache) + preselect nếu truyền vào */
  private loadPositionsByDepartment(deptId: number | null, preselectPositionId?: number | null): void {
    if (!deptId) { this.positions = []; return; }

    const key = Number(deptId);
    const cached = this.posCache.get(key);
    if (cached) {
      this.positions = cached;
      if (preselectPositionId != null) {
        this.editForm.get('positionId')?.setValue(preselectPositionId, { emitEvent: false });
        this.addForm.get('positionId')?.setValue(preselectPositionId, { emitEvent: false });
      }
      return;
    }

    this.positionService.getPositionsByDepartment(key).subscribe({
      next: res => {
        const list: PositionOption[] = (res?.data ?? []).map((p: PositionResponse) => ({
          id: p.id, name: p.name
        }));
        this.posCache.set(key, list);
        this.positions = list;

        if (preselectPositionId != null) {
          this.editForm.get('positionId')?.setValue(preselectPositionId, { emitEvent: false });
          this.addForm.get('positionId')?.setValue(preselectPositionId, { emitEvent: false });
        }
      },
      error: _ => { this.positions = []; }
    });
  }

  // ===== Search & Filter =====
  onSearch(event: Event): void {
    const term = (event.target as HTMLInputElement).value;
    this.searchTerms.next(term);
  }
  onFilterChange(): void { this.filterUsers(); }

  filterUsers(): void {
    let temp = [...this.users];
    const searchTerm = (this.searchTerms.value || '').toLowerCase();

    if (searchTerm) {
      temp = temp.filter(user =>
        (user.fullName || '').toLowerCase().includes(searchTerm) ||
        (user.email || '').toLowerCase().includes(searchTerm) ||
        (user.phone || '').includes(searchTerm)
      );
    }
    if (this.filterStatusTerm) temp = temp.filter(u => u.status === this.filterStatusTerm);
    if (this.filterRoleTerm)   temp = temp.filter(u => (u.roles || []).some(r => r.roleKey === this.filterRoleTerm));

    this.filteredUsers = temp;
  }

  roleLabels(roles?: RoleResponse[]): string {
    if (!roles || roles.length === 0) return 'N/A';
    return roles.map(r => r.description || r.roleKey).join(', ');
  }

  // ===== Modal handlers =====
  openAddUserModal(): void {
    this.addForm.reset({
      fullName: '', email: '', phone: '', role: '', departmentId: null, positionId: null,
    });
    this.positions = [];
    this.isAddModalOpen = true;
  }

  openEditUserModal(user: AuthProfileResponse): void {
    this.currentUserId = user.id;
    this.currentUserName = user.fullName;

    const depId = this.coerceNumOrNull((user as any).departmentId ?? (user as any).department?.id);
    const posId = this.coerceNumOrNull((user as any).positionId   ?? (user as any).position?.id);

    const proceed = () => {
      // 1) nạp position theo dept trước (để có options + preselect posId)
      this.loadPositionsByDepartment(depId, posId ?? null);

      // 2) patch form nhưng KHÔNG phát sự kiện để tránh valueChanges reset
      this.editForm.patchValue({
        id: user.id,
        fullName: user.fullName,
        email: user.email,
        phone: user.phone,
        roleKeys: (user.roles || []).map(r => r.roleKey),
        departmentId: depId,
        positionId: posId,
        status: user.status,
      }, { emitEvent: false });

      this.isEditModalOpen = true;
    };

    // Chưa có danh sách phòng ban -> tải rồi mới proceed
    if (!this.departmentsLoaded || this.departments.length === 0) {
      this.departmentService.getAllDepartments().subscribe({
        next: r => {
          this.departments = r.data || [];
          this.departmentsLoaded = true;
          proceed();
        },
        error: _ => {
          this.toastr.error('Không tải được phòng ban', 'Lỗi');
          // vẫn mở modal để người dùng thấy dữ liệu khác
          proceed();
        }
      });
    } else {
      proceed();
    }
  }

  openDeleteUserModal(userId: number, fullName: string): void {
    this.currentUserId = userId;
    this.currentUserName = fullName;
    this.isDeleteModalOpen = true;
  }

  openResetPasswordModal(userId: number, fullName: string): void {
    this.currentUserId = userId;
    this.currentUserName = fullName;
    this.isResetModalOpen = true;
  }

  closeModal(): void {
    this.isAddModalOpen = false;
    this.isEditModalOpen = false;
    this.isDeleteModalOpen = false;
    this.isResetModalOpen = false;
    this.addSubmitting = false;
    this.editSubmitting = false;
  }

  // ===== Actions =====
  onSubmitAdd(): void {
    if (this.addForm.invalid) {
      this.addForm.markAllAsTouched();
      this.toastr.warning('Vui lòng điền đủ thông tin bắt buộc', 'Thiếu dữ liệu');
      return;
    }
    this.addUser();
  }

  addUser(): void {
    if (this.addSubmitting) return;
    if (this.addForm.invalid) { this.addForm.markAllAsTouched(); return; }

    this.addSubmitting = true;

    const f = this.addForm.value;
    const roleKey = (f.role && (f.role.roleKey || f.role)) as string | undefined;

    const payload: AdminCreateUserRequest = {
      email: f.email,
      fullName: f.fullName,
      phone: f.phone,
      roleKeys: roleKey ? [roleKey] : [],
      departmentId: f.departmentId ?? null,
      positionId: f.positionId ?? null,
    };

    this.employeeService.createByAdmin(payload).subscribe({
      next: _ => {
        this.loadUsers();
        this.closeModal();
        this.toastr.success('Đã thêm người dùng thành công!', 'Thành công');
        this.addSubmitting = false;
      },
      error: err => {
        this.toastr.error(err?.error?.message || 'Lỗi tạo người dùng', 'Lỗi');
        this.addSubmitting = false;
      },
    });
  }

  updateUser(): void {
    if (this.editSubmitting) return;
    if (this.editForm.invalid || this.currentUserId == null) {
      this.editForm.markAllAsTouched();
      this.toastr.warning('Vui lòng kiểm tra lại thông tin', 'Thiếu dữ liệu');
      return;
    }

    const f = this.editForm.value;
    const payload = {
      fullName: f.fullName,
      email: f.email,
      phone: f.phone,
      roleKeys: (f.roleKeys || []) as string[],
      departmentId: f.departmentId ?? null,
      positionId: f.positionId ?? null,
      status: f.status,
    };

    this.editSubmitting = true;

    this.employeeService.updateByAdmin(this.currentUserId, payload).subscribe({
      next: _ => {
        this.loadUsers();
        this.closeModal();
        this.toastr.success('Cập nhật người dùng thành công!', 'Thành công');
        this.editSubmitting = false;
      },
      error: err => {
        this.toastr.error(err?.error?.message || 'Cập nhật người dùng thất bại', 'Lỗi');
        this.editSubmitting = false;
      }
    });
  }

  deleteUser(): void {
    this.toastr.info('API xoá người dùng chưa kết nối BE.', 'Thông tin');
    this.closeModal();
  }

  resetPassword(): void {
    this.toastr.info('(Demo) Đã reset mật khẩu — hãy nối API thật nếu đã có.', 'Thông tin');
    this.closeModal();
  }

  // ===== Helpers hiển thị =====
  displayDept(user: AuthProfileResponse): string {
    return (user as any).departmentName
        ?? (user as any).department?.name
        ?? '-';
  }

  displayPos(user: AuthProfileResponse): string {
    return (user as any).positionName
        ?? (user as any).position?.name
        ?? '-';
  }

  private coerceNumOrNull(v: unknown): number | null {
    if (v === undefined || v === null || v === '') return null;
    const n = Number(v);
    return Number.isNaN(n) ? null : n;
  }
}
