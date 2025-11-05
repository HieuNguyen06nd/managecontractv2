import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';  
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { CategoryService } from '../../core/services/category.service';
import { ToastrService } from 'ngx-toastr';
import {
  ContractTemplateResponse,
  TemplateVariable,
} from '../../core/models/contract-template-response.model';
import { Category } from '../../core/models/category.model';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-template-list',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './contact-list-template.component.html',
  styleUrls: ['./contact-list-template.component.scss']
})
export class ContactListTemplateComponent implements OnInit {
  templates: ContractTemplateResponse[] = [];
  categories: Category[] = [];
  loading = false;
  categoriesLoading = false;
  error = '';

  searchTerm = '';
  categoryFilter: number | null = null;
  statusFilter: '' | 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'PENDING' = '';

  pageSizeOptions: number[] = [6, 12, 24];
  pageSize = 6;
  currentPage = 1;

  // Modal
  showDetailModal = false;
  isEditMode: boolean = false; 
  selectedTemplate: ContractTemplateResponse = {
    id: 0,
    name: '',
    description: '',
    filePath: '',
    status: 'ACTIVE',
    variables: []
  };

  constructor(
    private templateService: ContractTemplateService,
    private categoryService: CategoryService,
    private router: Router,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.fetchTemplates();
    this.fetchCategories();
  }

  fetchTemplates(): void {
    this.loading = true;
    this.error = '';
    this.templateService.getAllTemplates().subscribe({
      next: (data) => {
        this.templates = data || [];
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Không thể tải danh sách hợp đồng mẫu.';
        console.error(err);
        this.loading = false;
      }
    });
  }

  fetchCategories(): void {
    this.categoriesLoading = true;
    this.categoryService.getAllCategories().subscribe({
      next: (response) => {
        this.categories = response?.data || [];
        this.categoriesLoading = false;
      },
      error: (err) => {
        console.error('Lỗi khi tải danh mục:', err);
        this.categories = [];
        this.categoriesLoading = false;
      }
    });
  }

  goToCreateTemplatePage(): void {
    this.router.navigate(['/contract/templates/create']);
  }

  updateTemplateStatus(template: ContractTemplateResponse): void {
    // Tính trạng thái mới (UPPERCASE)
    const newStatus: 'ACTIVE' | 'INACTIVE' =
      template.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';

    this.templateService
      .updateTemplateStatus(template.id, { status: newStatus })
      .subscribe({
        next: (res) => {
          // Đồng bộ theo BE trả về
          template.status = res.status as any;
          this.toastr.success('Trạng thái hợp đồng đã được cập nhật');
        },
        error: (err) => {
          this.toastr.error('Cập nhật trạng thái thất bại');
          console.error(err);
        },
      });
  }

  openDetail(t: ContractTemplateResponse, isEdit: boolean = false): void {
    this.selectedTemplate = { ...t };
    this.isEditMode = isEdit; 
    this.showDetailModal = true;
  }

  closeDetail(): void {
    this.showDetailModal = false;
    this.selectedTemplate = {
      id: 0,
      name: '',
      description: '',
      filePath: '',
      status: 'ACTIVE',
      variables: []
    };
    this.isEditMode = false; 
  }

  saveTemplate(): void {
    if (!this.selectedTemplate) {
      this.toastr.error('Không có template để cập nhật');
      return;
    }

    this.templateService.updateTemplate(this.selectedTemplate.id, this.selectedTemplate).subscribe({
      next: (response) => {
        this.toastr.success('Hợp đồng đã được cập nhật thành công');
        this.closeDetail();  
        this.fetchTemplates();
      },
      error: (err) => {
        this.toastr.error('Cập nhật hợp đồng thất bại');
        console.error(err);
      }
    });
  }

  getVariableTypeLabel(v: TemplateVariable): string {
    const raw = (v?.varType ?? 'TEXT');
    const type = String(raw).toUpperCase();
    const map: Record<string, string> = {
      'STRING': 'Văn bản',
      'TEXT': 'Văn bản',
      'NUMBER': 'Số',
      'DATE': 'Ngày tháng',
      'BOOLEAN': 'True/False',
      'TEXTAREA': 'Văn bản dài',
      'LIST': 'Danh sách',
      'DROPDOWN': 'Dropdown',
      'TABLE': 'Bảng',
      'CURRENCY': 'Tiền tệ',
    };
    return map[type] ?? 'Văn bản';
  }

  // Category helpers
  getTemplateCategoryLabel(t: ContractTemplateResponse): string {
    if (t.categoryName) {
      return t.categoryName;
    }
    
    if (t.categoryId && this.categories.length > 0) {
      const category = this.categories.find(c => c.id === t.categoryId);
      return category ? category.name : 'Khác';
    }
    
    // Fallback to categoryCode if no categoryId or categories not loaded
    const code = (t.categoryCode || '').toUpperCase();
    const byCode: Record<string, string> = {
      'LABOR': 'Lao động',
      'BUSINESS': 'Kinh doanh',
      'SERVICE': 'Dịch vụ',
      'RENTAL': 'Thuê',
      'LEGAL': 'Pháp lý',
    };
    return byCode[code] || 'Khác';
  }

  getCategoryName(categoryId: number): string {
    const category = this.categories.find(c => c.id === categoryId);
    return category ? category.name : 'Không xác định';
  }

  getStatusLabel(t: ContractTemplateResponse): string {
    return t.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động';
  }

  getStatusClass(t: ContractTemplateResponse): string {
    return t.status === 'ACTIVE' ? 'status-active' : 'status-inactive';
  }

  // Filter templates based on search and filters
  get filteredTemplates(): ContractTemplateResponse[] {
    let filtered = this.templates ?? [];

    // Search
    if (this.searchTerm) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(t => {
        const name = t.name?.toLowerCase() || '';
        const description = t.description?.toLowerCase() || '';
        const categoryLabel = this.getTemplateCategoryLabel(t).toLowerCase();
        return name.includes(term) || description.includes(term) || categoryLabel.includes(term);
      });
    }

    // Category
    if (this.categoryFilter) {
      filtered = filtered.filter(t => t.categoryId === this.categoryFilter);
    }

    // Status (CHUẨN HOÁ HOA)
    if (this.statusFilter) {
      const want = (this.statusFilter || '').toUpperCase();
      filtered = filtered.filter(t => (t.status || '').toUpperCase() === want);
    }

    return filtered;
  }

  // Pagination helpers
  get displayFrom(): number {
    return this.totalTemplates ? (this.currentPage - 1) * this.pageSize + 1 : 0;
  }

  get displayTo(): number {
    return Math.min(this.currentPage * this.pageSize, this.totalTemplates);
  }

  get totalTemplates(): number {
    return this.filteredTemplates.length;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalTemplates / this.pageSize));
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  get pagedTemplates(): ContractTemplateResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.filteredTemplates.slice(start, start + this.pageSize);
  }

  onChangePageSize(size: number | string): void {
    const n = Number(size) || this.pageSize;
    this.pageSize = n;
    this.currentPage = 1;
  }

  goToPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.currentPage = p;
  }

  prevPage(): void { this.goToPage(this.currentPage - 1); }
  nextPage(): void { this.goToPage(this.currentPage + 1); }
}