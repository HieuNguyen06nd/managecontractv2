import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';  
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { ToastrService } from 'ngx-toastr';
import {
  ContractTemplateResponse,
  TemplateVariable,
} from '../../core/models/contract-template-response.model';
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
  loading = false;
  error = '';

  searchTerm = '';
  categoryFilter: '' | 'LABOR' | 'BUSINESS' | 'SERVICE' | 'RENTAL' | 'LEGAL' = '';
  statusFilter: '' | 'active' | 'inactive' = '';

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
    status: 'active', // Mặc định là 'active'
    variables: [] // Khởi tạo biến mặc định
  };

  constructor(
    private templateService: ContractTemplateService,
    private router: Router,
    private toastr: ToastrService
  ) {}

  ngOnInit(): void {
    this.fetchTemplates();
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

  goToCreateTemplatePage(): void {
    this.router.navigate(['/contract/templates']);
  }

  updateTemplateStatus(template: ContractTemplateResponse): void {
    template.status = template.status === 'active' ? 'inactive' : 'active';
    this.templateService.updateTemplateStatus(template.id, { status: template.status }).subscribe({
      next: () => {
        this.toastr.success('Trạng thái hợp đồng đã được cập nhật');
        this.fetchTemplates();
      },
      error: (err) => {
        this.toastr.error('Cập nhật trạng thái thất bại');
        console.error(err);
      }
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
      status: 'active',
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
      TEXT: 'Text',
      NUMBER: 'Number',
      DATE: 'Date',
      CURRENCY: 'Currency',
    };
    return map[type] ?? 'Text';
  }

  // Add missing helper functions
  getTemplateCategoryLabel(t: ContractTemplateResponse): string {
    switch ((t.categoryCode || '').toUpperCase()) {
      case 'LABOR': return 'Lao động';
      case 'BUSINESS': return 'Kinh doanh';
      case 'SERVICE': return 'Dịch vụ';
      case 'RENTAL': return 'Thuê';
      case 'LEGAL': return 'Pháp lý';
      default: return 'Khác';
    }
  }

  getStatusLabel(t: ContractTemplateResponse): string {
    return t.status === 'active' ? 'Đang hoạt động' : 'Ngừng hoạt động';
  }

  getStatusClass(t: ContractTemplateResponse): string {
    return t.status === 'active' ? 'status-active' : 'status-inactive';
  }

  // Pagination helpers
  get displayFrom(): number {
    return this.totalTemplates ? (this.currentPage - 1) * this.pageSize + 1 : 0;
  }

  get displayTo(): number {
    return Math.min(this.currentPage * this.pageSize, this.totalTemplates);
  }

  get totalTemplates(): number {
    return this.templates.length;
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.totalTemplates / this.pageSize));
  }

  get pages(): number[] {
    return Array.from({ length: this.totalPages }, (_, i) => i + 1);
  }

  get pagedTemplates(): ContractTemplateResponse[] {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.templates.slice(start, start + this.pageSize);
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
