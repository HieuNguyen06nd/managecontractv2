import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, forkJoin } from 'rxjs';

import {
  DepartmentService,
  DepartmentResponse,
  DepartmentRequest,
  Status as DeptStatusApi,
} from '../../core/services/department.service';
import { AuthProfileResponse } from '../../core/models/auth.model';
import {
  EmployeeService
} from '../../core/services/employee.service';

// ===== UI models =====
interface Department {
  id: string; // hiển thị (server là number -> String)
  name: string;
  description?: string;
  manager?: Employee;
  employeeCount: number;
  status: 'active' | 'inactive';
}

interface Employee {
  id: string;   // hiển thị (server là number -> String)
  name: string; // fullName từ API
}

@Component({
  selector: 'app-department-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './department.component.html',
  styleUrls: ['./department.component.scss']
})
export class DepartmentComponent implements OnInit {
  // data
  departments: Department[] = [];
  filteredDepartments: Department[] = [];
  employees: Employee[] = [];

  // modals / forms
  isAddModalOpen = false;
  isEditModalOpen = false;
  isDeleteModalOpen = false;
  currentDepartmentId: string = '';
  deleteDepartmentName: string = '';

  addForm: FormGroup;
  editForm: FormGroup;

  // filter/search
  searchTerm: string = '';
  statusFilter: string = '';

  // feedback
  successMessage: string = '';
  errorMessage: string = '';

  // --- Phân trang ---
  pageSize = 10;
  currentPage = 1;
  totalItems = 0;
  totalPages = 1;

  private searchTerms = new Subject<string>();
  loading = false;

  constructor(
    private fb: FormBuilder,
    private departmentService: DepartmentService,
    private employeeService: EmployeeService
  ) {
    this.addForm = this.fb.group({
      // LƯU Ý: id ở đây chỉ để nhập/hiển thị, backend không dùng khi tạo
      id: [''],
      name: ['', Validators.required],
      description: [''],
      managerId: [''],
      status: ['active', Validators.required], // 'active' | 'inactive'
    });

    this.editForm = this.fb.group({
      id: [{ value: '', disabled: true }], // không cho sửa id backend
      name: ['', Validators.required],
      description: [''],
      managerId: [''],
      status: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.bindSearch();
    this.loadInitialData();
  }

  // ===== Load initial data from services =====
  private loadInitialData(): void {
    this.loading = true;
    forkJoin({
      depts: this.departmentService.getAllDepartments(), // ResponseData<DepartmentResponse[]>
      emps:  this.employeeService.getAll(),              // ResponseData<AuthProfileResponse[]>
    }).subscribe({
      next: ({ depts, emps }) => {
        const deptList = (depts?.data ?? []).map(d => this.mapDeptToUI(d));
        const empList  = (emps?.data ?? []).map(e => this.mapEmpToUI(e));

        this.departments = deptList;
        this.employees   = empList;
        this.filterDepartments();
        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.showError('Không tải được dữ liệu phòng ban/nhân viên.');
        this.loading = false;
      }
    });
  }

  // ===== Mapping helpers =====
  private apiStatusToUi(s: DeptStatusApi): 'active' | 'inactive' {
    return s === DeptStatusApi.ACTIVE ? 'active' : 'inactive';
  }
  private uiStatusToApi(s: 'active' | 'inactive'): DeptStatusApi {
    return s === 'active' ? DeptStatusApi.ACTIVE : DeptStatusApi.INACTIVE;
  }

  private mapDeptToUI(d: DepartmentResponse): Department {
    const anyD: any = d as any; // phòng trường hợp backend đã có description/employeeCount
    return {
      id: String(d.id),
      name: d.name,
      description: anyD.description ?? '',
      manager: d.leaderId ? { id: String(d.leaderId), name: d.leaderName ?? '' } : undefined,
      employeeCount: anyD.employeeCount ?? 0,
      status: this.apiStatusToUi(d.status),
    };
  }

  private mapEmpToUI(e: AuthProfileResponse): Employee {
    return {
      id: String(e.id),
      name: e.fullName ?? e.email ?? `#${e.id}`,
    };
  }

  // ===== Search/filter =====
  private bindSearch(): void {
    this.searchTerms
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.filterDepartments());
  }

  onSearch(event: any): void {
    this.searchTerm = event.target.value;
    this.searchTerms.next(this.searchTerm);
  }

  onFilterChange(): void {
    this.filterDepartments();
  }

filterDepartments(): void {
  // 1) Lọc theo search + trạng thái
  let tempDepts = this.departments;
  if (this.searchTerm) {
    const lowerSearch = this.searchTerm.toLowerCase();
    tempDepts = tempDepts.filter(dept =>
      dept.name.toLowerCase().includes(lowerSearch) ||
      (dept.description && dept.description.toLowerCase().includes(lowerSearch))
    );
  }
  if (this.statusFilter) {
    tempDepts = tempDepts.filter(dept => dept.status === this.statusFilter);
  }

  // 2) Tính phân trang
  this.totalItems = tempDepts.length;
  this.totalPages = Math.max(1, Math.ceil(this.totalItems / this.pageSize));
  if (this.currentPage > this.totalPages) this.currentPage = this.totalPages;

  const start = (this.currentPage - 1) * this.pageSize;
  const end = start + this.pageSize;

  // 3) Cắt trang hiện tại để hiển thị
  this.filteredDepartments = tempDepts.slice(start, end);
}


  // ===== Modals =====
  openAddDepartmentModal(): void {
    this.addForm.reset({ status: 'active' });
    this.isAddModalOpen = true;
  }

  openEditDepartmentModal(department: Department): void {
    this.currentDepartmentId = department.id;
    this.editForm.reset({
      id: department.id,
      name: department.name,
      description: department.description ?? '',
      managerId: department.manager?.id ?? '',
      status: department.status,
    });
    this.isEditModalOpen = true;
  }

  confirmDeleteDepartment(dept: Department): void {
    this.currentDepartmentId = dept.id;
    this.deleteDepartmentName = dept.name;
    this.isDeleteModalOpen = true;
  }

  closeModal(type: 'add' | 'edit' | 'delete'): void {
    if (type === 'add') this.isAddModalOpen = false;
    if (type === 'edit') this.isEditModalOpen = false;
    if (type === 'delete') this.isDeleteModalOpen = false;
  }

  // ===== CRUD via services =====
  addDepartment(): void {
    if (this.addForm.invalid) return;

    const v = this.addForm.getRawValue();
    const payload: DepartmentRequest = {
      name: v.name,
      description: v.description || undefined,
      leaderId: v.managerId ? Number(v.managerId) : undefined,
      status: this.uiStatusToApi(v.status),
      // nếu có dùng level/parentId thì bổ sung ở đây
    };

    this.loading = true;
    this.departmentService.createDepartment(payload).subscribe({
      next: res => {
        const created = this.mapDeptToUI(res.data);
        this.departments = [created, ...this.departments];
        this.filterDepartments();
        this.closeModal('add');
        this.showSuccess('Đã thêm phòng ban thành công!');
        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.showError('Thêm phòng ban thất bại.');
        this.loading = false;
      }
    });
  }

  updateDepartment(): void {
    if (this.editForm.invalid || !this.currentDepartmentId) return;

    const v = this.editForm.getRawValue();
    const payload: DepartmentRequest = {
      name: v.name,
      description: v.description || undefined,
      leaderId: v.managerId ? Number(v.managerId) : undefined,
      status: this.uiStatusToApi(v.status),
    };

    this.loading = true;
    this.departmentService.updateDepartment(Number(this.currentDepartmentId), payload).subscribe({
      next: res => {
        const updated = this.mapDeptToUI(res.data);
        const idx = this.departments.findIndex(d => d.id === this.currentDepartmentId);
        if (idx > -1) this.departments[idx] = updated;
        this.filterDepartments();
        this.closeModal('edit');
        this.showSuccess('Đã cập nhật phòng ban thành công!');
        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.showError('Cập nhật phòng ban thất bại.');
        this.loading = false;
      }
    });
  }

  deleteDepartment(): void {
    if (!this.currentDepartmentId) return;
    this.loading = true;
    this.departmentService.deleteDepartment(Number(this.currentDepartmentId)).subscribe({
      next: () => {
        this.departments = this.departments.filter(d => d.id !== this.currentDepartmentId);
        this.filterDepartments();
        this.closeModal('delete');
        this.showSuccess('Đã xóa phòng ban thành công!');
        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.showError('Xóa phòng ban thất bại.');
        this.loading = false;
      }
    });
  }

get pageList(): number[] {
  const maxButtons = 5;
  const half = Math.floor(maxButtons / 2);
  let start = Math.max(1, this.currentPage - half);
  let end = Math.min(this.totalPages, start + maxButtons - 1);
  // dịch lại start nếu không đủ 5 nút ở cuối
  start = Math.max(1, Math.min(start, end - maxButtons + 1));

  const pages: number[] = [];
  for (let p = start; p <= end; p++) pages.push(p);
  return pages;
}

goToPage(p: number): void {
  if (p < 1 || p > this.totalPages) return;
  this.currentPage = p;
  this.filterDepartments(); // tính lại slice hiển thị
}

prevPage(): void {
  if (this.currentPage > 1) {
    this.currentPage--;
    this.filterDepartments();
  }
}

nextPage(): void {
  if (this.currentPage < this.totalPages) {
    this.currentPage++;
    this.filterDepartments();
  }
}

onPageSizeChange(): void {
  this.currentPage = 1;
  this.filterDepartments();
}

  // ===== feedback helpers =====
  showSuccess(message: string): void {
    this.successMessage = message;
    setTimeout(() => (this.successMessage = ''), 2500);
  }

  showError(message: string): void {
    this.errorMessage = message;
    setTimeout(() => (this.errorMessage = ''), 3000);
  }
}
