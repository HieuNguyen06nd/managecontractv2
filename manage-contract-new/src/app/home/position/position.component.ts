import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';

import { PositionService } from '../../core/services/position.service';
import { DepartmentService } from '../../core/services/department.service';

import { ResponseData } from '../../core/models/response-data.model';
import { PositionResponse, PositionRequest, Status } from '../../core/models/position.model';
import { DepartmentResponse } from '../../core/models/department.model'; // nếu bạn không có file này, import từ service: '../../core/services/department.service'

@Component({
  selector: 'app-position',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './position.component.html',
  styleUrls: ['./position.component.scss']
})
export class PositionComponent implements OnInit {

  positions: PositionResponse[] = [];
  filteredPositions: PositionResponse[] = [];
  departments: DepartmentResponse[] = [];

  // filters
  searchTerm = '';
  departmentFilter = '';
  statusFilter: '' | 'ACTIVE' | 'INACTIVE' | 'LOCKED' = '';

  // pagination
  currentPage = 1;
  pageSize = 5;
  totalPages = 1;

  // modals
  showAddModal = false;
  showEditModal = false;
  showDeleteModal = false;

  selectedPosition: PositionResponse | null = null;

  // BẮT BUỘC có departmentId
  formData: PositionRequest = { name: '', description: '', status: Status.ACTIVE, departmentId: 0 };

  constructor(
    private positionService: PositionService,
    private departmentService: DepartmentService,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.loadDepartments();
    this.loadPositions();
  }

  // ===== LOAD DATA =====
  loadDepartments(): void {
    this.departmentService.getAllDepartments().subscribe({
      next: (res: ResponseData<DepartmentResponse[]>) => {
        this.departments = res?.data ?? [];
      },
      error: (err) => {
        console.error(err);
        this.departments = [];
        this.toastr.error('Không thể tải danh sách phòng ban');
      }
    });
  }

  loadPositions(): void {
    this.positionService.getAllPositions().subscribe({
      next: (res: ResponseData<PositionResponse[]>) => {
        this.positions = res?.data ?? [];
        this.currentPage = 1;
        this.filterPositions();
      },
      error: (err) => {
        console.error(err);
        this.positions = [];
        this.filteredPositions = [];
        this.totalPages = 1;
        this.toastr.error('Không thể tải danh sách vị trí');
      }
    });
  }

  // ===== PAGINATION / FILTER =====
  get paginatedPositions(): PositionResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredPositions.slice(start, start + this.pageSize);
  }

  onFilterChange(): void {
    this.currentPage = 1;
    this.filterPositions();
  }

  filterPositions(): void {
    const keyword = this.normalize(this.searchTerm);
    const deptKw = this.normalize(this.departmentFilter);
    const status = this.statusFilter;

    let result = this.positions.filter(p => {
      const name = this.normalize(p.name);
      const desc = this.normalize(p.description || '');

      // Nếu BE chưa trả kèm tên phòng ban, bạn có thể map từ danh sách departments:
      const deptName =
        this.normalize((p as any).departmentName || (p as any).department || '') ||
        this.normalize(this.departments.find(d => d.id === p.departmentId)?.name || '');

      const matchesSearch = !keyword || name.includes(keyword) || desc.includes(keyword);
      const matchesDepartment = !deptKw || deptName.includes(deptKw) || desc.includes(deptKw);
      const matchesStatus = !status || p.status === status;

      return matchesSearch && matchesDepartment && matchesStatus;
    });

    this.filteredPositions = result;

    this.totalPages = Math.max(1, Math.ceil(this.filteredPositions.length / this.pageSize));
    if (this.currentPage > this.totalPages) {
      this.currentPage = this.totalPages;
    }
  }

  changePage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
  }

  // ===== CRUD =====
  openAddModal(): void {
    // mặc định chọn phòng ban đầu tiên (nếu có)
    const defaultDeptId = this.departments.length ? this.departments[0].id : 0;
    this.formData = { name: '', description: '', status: Status.ACTIVE, departmentId: defaultDeptId };
    this.showAddModal = true;
  }

  addPosition(): void {
    if (!this.formData.name?.trim()) {
      this.toastr.error('Tên vị trí là bắt buộc');
      return;
    }
    if (!this.formData.departmentId || this.formData.departmentId <= 0) {
      this.toastr.error('Vui lòng chọn phòng ban');
      return;
    }

    this.positionService.createPosition(this.formData).subscribe({
      next: () => {
        this.toastr.success('Thêm vị trí thành công');
        this.closeModals();
        this.loadPositions();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể thêm vị trí');
      }
    });
  }

  openEditModal(position: PositionResponse): void {
    this.selectedPosition = { ...position };
    this.formData = {
      name: position.name,
      description: position.description || '',
      status: position.status,
      departmentId: position.departmentId || 0
    };
    this.showEditModal = true;
  }

  updatePosition(): void {
    if (!this.selectedPosition) return;
    if (!this.formData.name?.trim()) {
      this.toastr.error('Tên vị trí là bắt buộc');
      return;
    }
    if (!this.formData.departmentId || this.formData.departmentId <= 0) {
      this.toastr.error('Vui lòng chọn phòng ban');
      return;
    }

    this.positionService.updatePosition(this.selectedPosition.id, this.formData).subscribe({
      next: () => {
        this.toastr.success('Cập nhật vị trí thành công');
        this.closeModals();
        this.loadPositions();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể cập nhật vị trí');
      }
    });
  }

  openDeleteModal(position: PositionResponse): void {
    this.selectedPosition = position;
    this.showDeleteModal = true;
  }

  deletePosition(): void {
    if (!this.selectedPosition) return;
    this.positionService.deletePosition(this.selectedPosition.id).subscribe({
      next: () => {
        this.toastr.success('Xóa vị trí thành công');
        this.closeModals();
        const totalAfter = this.filteredPositions.length - 1;
        const newTotalPages = Math.max(1, Math.ceil(totalAfter / this.pageSize));
        if (this.currentPage > newTotalPages) this.currentPage = newTotalPages;
        this.loadPositions();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể xóa vị trí');
      }
    });
  }

  closeModals(): void {
    this.showAddModal = false;
    this.showEditModal = false;
    this.showDeleteModal = false;
    this.selectedPosition = null;
  }

  trackByPositionId(_i: number, p: PositionResponse) {
    return p.id;
  }

  private normalize(s: string): string {
    return (s || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  }
}
