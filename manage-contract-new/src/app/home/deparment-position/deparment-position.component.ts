import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';

import {
  DepartmentService,
  DepartmentResponse,
  DepartmentRequest,
  Status as DeptStatus
} from '../../core/services/department.service';

import {
  PositionService,
  Status as PosStatus
} from '../../core/services/position.service';
import { PositionResponse, PositionRequest } from '../../core/models/position.model';

type UiStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED';

interface DepartmentUi {
  id: number;
  name: string;
  level: number;
  parentId?: number;
  parentName?: string;
  leaderId?: number;
  leaderName?: string;
  status: UiStatus;
  expanded?: boolean;
  positionsLoaded?: boolean;
  positions: PositionResponse[];
}

@Component({
  selector: 'app-deparment-position',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './deparment-position.component.html',
  styleUrls: ['./deparment-position.component.scss']
})
export class DeparmentPositionComponent {
  // ===== Filters/UI =====
  searchTerm = '';
  statusFilter: 'all' | UiStatus = 'all';
  departmentFilter: 'all' | string = 'all';

  // ===== Data =====
  departments: DepartmentUi[] = [];

  // ===== Modal state & forms =====
  showDeptModal = false;
  editingDepartment = false;
  deptForm: {
    id: number | null;
    name: string;
    level: number;
    parentId?: string;
    leaderId?: string;
    status: UiStatus;
  } = { id: null, name: '', level: 1, parentId: '', status: 'ACTIVE' };

  showPosModal = false;
  editingPosition = false;
  posForm: {
    id: number | null;
    name: string;
    description: string;
    departmentId: string;   // select
    status: UiStatus;
    originalDepartmentId: number | null;
  } = { id: null, name: '', description: '', departmentId: '', status: 'ACTIVE', originalDepartmentId: null };

  loading = false;

  constructor(
    private departmentService: DepartmentService,
    private positionService: PositionService,
    private toastr: ToastrService
  ) {
    this.loadDepartments();
  }

  // ===== Load departments =====
  loadDepartments(): void {
    this.loading = true;
    this.departmentService.getAllDepartments().subscribe({
      next: (res) => {
        const data = res?.data ?? [];
        this.departments = data.map((d: DepartmentResponse) => ({
          id: d.id,
          name: d.name,
          level: d.level,
          parentId: d.parentId,
          parentName: d.parentName,
          leaderId: d.leaderId,
          leaderName: d.leaderName,
          status: d.status as UiStatus,
          expanded: false,
          positionsLoaded: false,
          positions: []
        }));
        this.loading = false;
      },
      error: (err) => {
        console.error(err);
        this.showError('Không tải được danh sách phòng ban.');
        this.loading = false;
      }
    });
  }

  // ===== Computed lists =====
  get filteredDepartments(): DepartmentUi[] {
    let list = [...this.departments];

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase();
      list = list.filter(d =>
        d.name.toLowerCase().includes(term) ||
        (d.leaderName ?? '').toLowerCase().includes(term) ||
        d.positions.some(p =>
          p.name.toLowerCase().includes(term) ||
          (p.description ?? '').toLowerCase().includes(term)
        )
      );
    }

    if (this.statusFilter !== 'all') {
      list = list.filter(d => d.status === this.statusFilter);
    }

    if (this.departmentFilter !== 'all') {
      list = list.filter(d => String(d.id) === this.departmentFilter);
    }

    return list;
  }

  get activeDepartments(): DepartmentUi[] {
    return this.departments.filter(d => d.status === 'ACTIVE');
  }

  // ===== Expand / Lazy load positions =====
  toggleExpand(dept: DepartmentUi): void {
    dept.expanded = !dept.expanded;
    if (dept.expanded && !dept.positionsLoaded) {
      this.loadPositionsForDepartment(dept);
    }
  }

  loadPositionsForDepartment(dept: DepartmentUi): void {
    this.positionService.getPositionsByDepartment(dept.id).subscribe({
      next: res => {
        dept.positions = res?.data ?? [];
        dept.positionsLoaded = true;
      },
      error: err => {
        console.error(err);
        this.showError(`Không tải được vị trí của phòng ban "${dept.name}".`);
      }
    });
  }

  // ===== Toast helpers =====
  private showSuccess(msg: string): void {
    this.toastr.success(msg, 'Thành công');
  }
  private showError(msg: string): void {
    this.toastr.error(msg, 'Lỗi');
  }
  private showInfo(msg: string): void {
    this.toastr.info(msg, 'Thông báo');
  }
  private showWarning(msg: string): void {
    this.toastr.warning(msg, 'Cảnh báo');
  }

  // ===== Department Modal =====
  openAddDepartment(): void {
    this.editingDepartment = false;
    this.deptForm = { id: null, name: '', level: 1, parentId: '', status: 'ACTIVE' };
    this.showDeptModal = true;
  }

  openEditDepartment(d: DepartmentUi): void {
    this.editingDepartment = true;
    this.deptForm = {
      id: d.id,
      name: d.name,
      level: d.level,
      parentId: d.parentId ? String(d.parentId) : '',
      leaderId: d.leaderId ? String(d.leaderId) : '',
      status: d.status
    };
    this.showDeptModal = true;
  }

  closeDeptModal(): void {
    this.showDeptModal = false;
  }

  // ===== Department Save/Delete =====
  saveDepartment(): void {
    const f = this.deptForm;
    const payload: DepartmentRequest = {
      name: f.name,
      level: f.level,
      parentId: f.parentId ? Number(f.parentId) : undefined,
      status: f.status as DeptStatus
    };

    if (this.editingDepartment && f.id != null) {
      this.departmentService.updateDepartment(f.id, payload).subscribe({
        next: (res) => {
          const updated = res.data;
          const idx = this.departments.findIndex(x => x.id === f.id);
          if (idx > -1) {
            this.departments[idx] = {
              ...this.departments[idx],
              name: updated.name,
              level: updated.level,
              parentId: updated.parentId,
              parentName: updated.parentName,
              leaderId: updated.leaderId,
              leaderName: updated.leaderName,
              status: updated.status as UiStatus
            };
          }
          this.showSuccess('Cập nhật phòng ban thành công!');
          this.closeDeptModal();
        },
        error: (err) => {
          console.error(err);
          this.showError('Cập nhật phòng ban thất bại.');
        }
      });
    } else {
      this.departmentService.createDepartment(payload).subscribe({
        next: (res) => {
          const d = res.data;
          this.departments.unshift({
            id: d.id,
            name: d.name,
            level: d.level,
            parentId: d.parentId,
            parentName: d.parentName,
            leaderId: d.leaderId,
            leaderName: d.leaderName,
            status: d.status as UiStatus,
            expanded: false,
            positionsLoaded: false,
            positions: []
          });
          this.showSuccess('Thêm phòng ban thành công!');
          this.closeDeptModal();
        },
        error: (err) => {
          console.error(err);
          this.showError('Thêm phòng ban thất bại.');
        }
      });
    }
  }

  deleteDepartment(id: number): void {
    if (!confirm('Bạn có chắc chắn muốn xóa phòng ban này?')) return;
    this.departmentService.deleteDepartment(id).subscribe({
      next: () => {
        this.departments = this.departments.filter(d => d.id !== id);
        this.showSuccess('Xóa phòng ban thành công!');
      },
      error: (err) => {
        console.error(err);
        this.showError('Xóa phòng ban thất bại.');
      }
    });
  }

  // ===== Position Modal =====
  openAddPosition(deptId?: number): void {
    const defaultDeptId = deptId ?? this.activeDepartments[0]?.id;
    if (!defaultDeptId) {
      this.showError('Không có phòng ban đang hoạt động để thêm vị trí.');
      return;
    }
    this.editingPosition = false;
    this.posForm = {
      id: null,
      name: '',
      description: '',
      departmentId: String(defaultDeptId),
      status: 'ACTIVE',
      originalDepartmentId: null
    };
    this.showPosModal = true;
  }

  openEditPosition(pos: PositionResponse, departmentId: number): void {
    this.editingPosition = true;
    this.posForm = {
      id: pos.id,
      name: pos.name,
      description: pos.description || '',
      departmentId: String(departmentId),
      status: pos.status as UiStatus,
      originalDepartmentId: departmentId
    };
    this.showPosModal = true;
  }

  closePosModal(): void {
    this.showPosModal = false;
  }

  // ===== Position Save/Delete =====
  savePosition(): void {
    const f = this.posForm;
    const targetDeptId = Number(f.departmentId);

    const payload: PositionRequest = {
      name: f.name,
      description: f.description || undefined,
      status: f.status as PosStatus,
      departmentId: targetDeptId
    };

    // Update
    if (this.editingPosition && f.id != null) {
      this.positionService.updatePosition(f.id, payload).subscribe({
        next: () => {
          if (f.originalDepartmentId && f.originalDepartmentId !== targetDeptId) {
            const fromDept = this.departments.find(d => d.id === f.originalDepartmentId);
            const toDept = this.departments.find(d => d.id === targetDeptId);
            if (fromDept) { fromDept.positionsLoaded = false; this.loadPositionsForDepartment(fromDept); }
            if (toDept)   { toDept.expanded = true; toDept.positionsLoaded = false; this.loadPositionsForDepartment(toDept); }
          } else {
            const dept = this.departments.find(d => d.id === targetDeptId);
            if (dept) { dept.expanded = true; dept.positionsLoaded = false; this.loadPositionsForDepartment(dept); }
          }
          this.showSuccess('Cập nhật vị trí thành công!');
          this.closePosModal();
        },
        error: (err) => {
          console.error(err);
          this.showError('Cập nhật vị trí thất bại.');
        }
      });
      return;
    }

    // Create
    this.positionService.createPosition(payload).subscribe({
      next: () => {
        const dept = this.departments.find(d => d.id === targetDeptId);
        if (dept) {
          dept.expanded = true;
          dept.positionsLoaded = false;          // buộc reload để thấy item mới
          this.loadPositionsForDepartment(dept);
        }
        this.showSuccess('Thêm vị trí thành công!');
        this.closePosModal();
      },
      error: (err) => {
        console.error(err);
        this.showError('Thêm vị trí thất bại.');
      }
    });
  }

  deletePosition(positionId: number, departmentId: number): void {
    if (!confirm('Bạn có chắc chắn muốn xóa vị trí này?')) return;
    this.positionService.deletePosition(positionId).subscribe({
      next: () => {
        const dept = this.departments.find(d => d.id === departmentId);
        if (dept) { dept.positionsLoaded = false; this.loadPositionsForDepartment(dept); }
        this.showSuccess('Xóa vị trí thành công!');
      },
      error: (err) => {
        console.error(err);
        this.showError('Xóa vị trí thất bại.');
      }
    });
  }
}
