import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { PositionService } from '../../core/services/position.service';
import { ResponseData } from '../../core/models/response-data.model';
import { PositionResponse, PositionRequest, Status } from '../../core/models/position.model';

@Component({
  selector: 'app-position',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './position.component.html',
  styleUrls: ['./position.component.scss']
})
export class PositionComponent implements OnInit {

  positions: PositionResponse[] = [];
  filteredPositions: PositionResponse[] = []; // dữ liệu đã lọc, CHƯA cắt trang

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

  formData: PositionRequest = { name: '', description: '', status: Status.ACTIVE };

  constructor(private positionService: PositionService, private toastr: ToastrService) {}

  ngOnInit(): void {
    this.loadPositions();
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

  /** Getter trả về danh sách đã cắt theo trang để hiển thị */
  get paginatedPositions(): PositionResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredPositions.slice(start, start + this.pageSize);
  }

  /** Gọi khi thay đổi input/select filter */
  onFilterChange(): void {
    this.currentPage = 1;
    this.filterPositions();
  }

  /** Lọc dữ liệu theo search + department + status, đồng thời tính totalPages */
  filterPositions(): void {
    const keyword = this.normalize(this.searchTerm);
    const deptKw = this.normalize(this.departmentFilter);
    const status = this.statusFilter;

    let result = this.positions.filter(p => {
      const name = this.normalize(p.name);
      const desc = this.normalize(p.description || '');

      // nếu có thêm field phòng ban trong response, hỗ trợ luôn:
      const deptName = this.normalize((p as any).departmentName || (p as any).department || '');

      const matchesSearch = !keyword || name.includes(keyword) || desc.includes(keyword);
      const matchesDepartment = !deptKw || deptName.includes(deptKw) || desc.includes(deptKw);
      const matchesStatus = !status || p.status === status;

      return matchesSearch && matchesDepartment && matchesStatus;
    });

    this.filteredPositions = result;

    // tính lại totalPages và chốt currentPage trong biên
    this.totalPages = Math.max(1, Math.ceil(this.filteredPositions.length / this.pageSize));
    if (this.currentPage > this.totalPages) {
      this.currentPage = this.totalPages;
    }
  }

  /** Chuyển trang */
  changePage(page: number): void {
    if (page < 1 || page > this.totalPages) return;
    this.currentPage = page;
    // không cần gọi filter lại vì filteredPositions đã có, getter sẽ cắt trang
  }

  /** Thêm */
  openAddModal(): void {
    this.formData = { name: '', description: '', status: Status.ACTIVE };
    this.showAddModal = true;
  }

  addPosition(): void {
    if (!this.formData.name?.trim()) {
      this.toastr.error('Tên vị trí là bắt buộc');
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

  /** Sửa */
  openEditModal(position: PositionResponse): void {
    this.selectedPosition = { ...position };
    this.formData = {
      name: position.name,
      description: position.description || '',
      status: position.status
    };
    this.showEditModal = true;
  }

  updatePosition(): void {
    if (!this.selectedPosition) return;
    if (!this.formData.name?.trim()) {
      this.toastr.error('Tên vị trí là bắt buộc');
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

  /** Xóa */
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
        // nếu xóa phần tử cuối của trang cuối -> lùi trang
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

  /** Đóng modal */
  closeModals(): void {
    this.showAddModal = false;
    this.showEditModal = false;
    this.showDeleteModal = false;
    this.selectedPosition = null;
  }

  /** trackBy để tránh re-render không cần thiết */
  trackByPositionId(_i: number, p: PositionResponse) {
    return p.id;
  }

  /** Chuẩn hóa để tìm kiếm không phân biệt hoa thường/dấu tiếng Việt */
  private normalize(s: string): string {
    return (s || '')
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, ''); // bỏ dấu
  }
}
