// src/app/home/user/user.component.ts
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormControl } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EmployeeService, AdminCreateUserRequest } from '../../core/services/employee.service';
import { AuthProfileResponse, RoleResponse, StatusUser } from '../../core/models/auth.model';
import { BehaviorSubject, debounceTime, distinctUntilChanged } from 'rxjs';
import { DepartmentService } from '../../core/services/department.service';
import { DepartmentResponse } from '../../core/models/department.model';
import { RoleService } from '../../core/services/role.service';
import { PositionService } from '../../core/services/position.service';

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
  users: AuthProfileResponse[] = [];
  filteredUsers: AuthProfileResponse[] = [];

  roles: RoleOption[] = [];
  departments: DepartmentResponse[] = [];
  positions: PositionOption[] = [];

  isAddModalOpen = false;
  isEditModalOpen = false;
  isDeleteModalOpen = false;
  isResetModalOpen = false;

  currentUserId: number | null = null;
  currentUserName = '';

  addForm: FormGroup;
  editForm: FormGroup;

  searchTerms = new BehaviorSubject<string>('');
  filterStatusTerm: string = ''; // 'ACTIVE' | 'INACTIVE' | 'PENDING' | 'LOCKED'
  filterRoleTerm: string = '';   // roleKey

  constructor(
    private fb: FormBuilder,
    private employeeService: EmployeeService,
    private departmentService: DepartmentService,
    private roleService: RoleService,
    private positionService: PositionService
  ) {
    this.addForm = this.fb.group({
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      role: ['', Validators.required],       // chọn 1 vai trò (sau đó wrap thành roleKeys[])
      departmentId: [null],
      positionId: [null],
    });

    this.editForm = this.fb.group({
      id: [null],
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      roleKeys: new FormControl<string[]>([]), // có thể sửa sau khi có API update
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
  }

  loadUsers(): void {
    this.employeeService.getAll().subscribe(res => {
      this.users = res.data || [];
      this.filterUsers();
    });
  }

  loadDropdowns(): void {
    this.roleService.getAll().subscribe(r => {
      const arr = r.data || [];
      this.roles = arr.map(x => ({ roleKey: x.roleKey, description: x.description }));
    });
    this.departmentService.getAllDepartments().subscribe(r => {
      this.departments = r.data || [];
    });
    this.positionService.getAllPositions().subscribe(r => {
      const arr = r.data || [];
      this.positions = arr.map(p => ({ id: p.id, name: p.name }));
    });
  }

  onSearch(event: Event): void {
    const term = (event.target as HTMLInputElement).value;
    this.searchTerms.next(term);
  }

  onFilterChange(): void {
    this.filterUsers();
  }

  filterUsers(): void {
    let temp = [...this.users];

    // search
    const searchTerm = (this.searchTerms.value || '').toLowerCase();
    if (searchTerm) {
      temp = temp.filter(user =>
        (user.fullName || '').toLowerCase().includes(searchTerm) ||
        (user.email || '').toLowerCase().includes(searchTerm) ||
        (user.phone || '').includes(searchTerm)
      );
    }

    // status
    if (this.filterStatusTerm) {
      temp = temp.filter(u => u.status === this.filterStatusTerm);
    }

    // role (so sánh theo roleKey)
    if (this.filterRoleTerm) {
      temp = temp.filter(u => (u.roles || []).some(r => r.roleKey === this.filterRoleTerm));
    }

    this.filteredUsers = temp;
  }

  roleLabels(roles?: RoleResponse[]): string {
    if (!roles || roles.length === 0) return 'N/A';
    return roles.map(r => r.description || r.roleKey).join(', ');
  }

  openAddUserModal(): void {
    this.addForm.reset({
      fullName: '',
      email: '',
      phone: '',
      role: '',
      departmentId: null,
      positionId: null,
    });
    this.isAddModalOpen = true;
  }

  openEditUserModal(user: AuthProfileResponse): void {
    this.currentUserId = user.id;
    this.currentUserName = user.fullName;

    // departmentId: ưu tiên field departmentId nếu BE trả; nếu department là object thì lấy .id; nếu là string thì null
    const depId =
      (user as any).departmentId ??
      (user.department && typeof user.department === 'object'
        ? (user.department as any).id ?? null
        : null);

    // positionId: tương tự
    const posId =
      (user as any).positionId ??
      (user.position && typeof user.position === 'object'
        ? (user.position as any).id ?? null
        : null);

    this.editForm.patchValue({
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      phone: user.phone,
      roleKeys: (user.roles || []).map(r => r.roleKey),
      departmentId: depId,
      positionId: posId,
      status: user.status,
    });

    this.isEditModalOpen = true;
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
  }

  /** Tạo user qua /api/auth/users (BE sẽ gửi mật khẩu tạm & bắt đổi lần đầu) */
  addUser(): void {
    if (this.addForm.invalid) return;

    const f = this.addForm.value;
    const roleKey = (f.role && (f.role.roleKey || f.role)) as string | undefined;

    const payload: AdminCreateUserRequest = {
      email: f.email,
      fullName: f.fullName,
      phone: f.phone,
      roleKeys: roleKey ? [roleKey] : [],   // wrap mảng theo BE
      departmentId: f.departmentId ?? null,
      positionId: f.positionId ?? null,
    };

    this.employeeService.createByAdmin(payload).subscribe({
      next: _ => {
        this.loadUsers();
        this.closeModal();
        alert('Đã thêm người dùng thành công!');
      },
      error: err => alert(err?.error?.message || 'Lỗi tạo người dùng'),
    });
  }


  displayDept(user: AuthProfileResponse): string {
    const d: any = (user as any).department ?? (user as any).departmentName;
    if (!d) return '-';
    return typeof d === 'string' ? d : (d.name ?? '-');
  }

  displayPos(user: AuthProfileResponse): string {
    const p: any = (user as any).position ?? (user as any).positionName;
    if (!p) return '-';
    return typeof p === 'string' ? p : (p.name ?? '-');
  }

  updateUser(): void {
    // TODO: Nối với API update người dùng khi sẵn sàng
    alert('API cập nhật người dùng chưa kết nối BE.');
  }

  deleteUser(): void {
    // TODO: Nối với API xoá người dùng khi sẵn sàng
    alert('API xoá người dùng chưa kết nối BE.');
    this.closeModal();
  }

  resetPassword(): void {
    // TODO: Nối với API reset mật khẩu bởi admin (nếu đã có)
    this.closeModal();
    alert('(Demo) Đã reset mật khẩu — hãy nối API thật nếu đã có.');
  }
}
