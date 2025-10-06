import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { ContractTemplateService } from '../../core/services/contract-template.service';
import {
  ContractTemplateResponse,
  TemplateVariable,
} from '../../core/models/contract-template-response.model';

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

  // Filters
  searchTerm = '';
  /** filter theo code danh mục viết HOA: '', 'LABOR', 'BUSINESS', 'SERVICE', 'RENTAL', 'LEGAL' */
  categoryFilter: '' | 'LABOR' | 'BUSINESS' | 'SERVICE' | 'RENTAL' | 'LEGAL' = '';
  statusFilter: '' | 'active' | 'inactive' = '';

  // Pagination
  pageSizeOptions: number[] = [6, 12, 24];
  pageSize = 6;
  currentPage = 1;

  // Modal
  showDetailModal = false;
  selectedTemplate: ContractTemplateResponse | null = null;

  constructor(private templateService: ContractTemplateService) {}

  ngOnInit(): void {
    this.fetchTemplates();
  }

  fetchTemplates(): void {
    this.loading = true;
    this.error = '';
    this.templateService.getAllTemplates().subscribe({
      next: (data) => {
        this.templates = data || [];
        this.currentPage = 1;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Không thể tải danh sách hợp đồng mẫu.';
        console.error(err);
        this.loading = false;
      }
    });
  }

  // ===== Variable helpers =====
  getVariableTypeLabel(v: TemplateVariable): string {
    const raw = (v?.varType ?? (v as any)?.type ?? 'TEXT');
    const type = String(raw).toUpperCase();
    const map: Record<string, string> = {
      TEXT: 'Text',
      NUMBER: 'Number',
      DATE: 'Date',
      CURRENCY: 'Currency',
    };
    return map[type] ?? 'Text';
  }

  

  // ===== Category helpers =====
  private mapCategoryCodeToLabel(code?: string): string {
    switch ((code || '').toUpperCase()) {
      case 'LABOR': return 'Lao động';
      case 'BUSINESS': return 'Kinh doanh';
      case 'SERVICE': return 'Dịch vụ';
      case 'RENTAL': return 'Thuê';
      case 'LEGAL': return 'Pháp lý';
      default: return 'Khác';
    }
  }
  /** Nhãn hiển thị ưu tiên name từ API, fallback theo code */
  getTemplateCategoryLabel(t: ContractTemplateResponse): string {
    return t.categoryName || this.mapCategoryCodeToLabel(t.categoryCode);
  }
  private getCategoryCode(t: ContractTemplateResponse): string {
    return (t.categoryCode || '').toUpperCase();
  }

  // ===== Status helpers (tùy UI; Template có thể chưa có status từ API) =====
  getStatusKey(_t: ContractTemplateResponse): 'active'|'inactive' {
    // Nếu BE có field status cho Template, sửa logic tại đây.
    return 'active';
  }
  getStatusLabel(t: ContractTemplateResponse): string {
    return this.getStatusKey(t) === 'active' ? 'Đang hoạt động' : 'Ngừng hoạt động';
  }
  getStatusClass(t: ContractTemplateResponse): string {
    return this.getStatusKey(t) === 'active' ? 'status-active' : 'status-inactive';
  }

  // ===== Search + Filter =====
  private matchesSearch(t: ContractTemplateResponse): boolean {
    if (!this.searchTerm) return true;
    const q = this.searchTerm.trim().toLowerCase();
    const name = (t.name || '').toLowerCase();
    const desc = (t.description || '').toLowerCase();
    const catName = this.getTemplateCategoryLabel(t).toLowerCase();
    const catCode = this.getCategoryCode(t).toLowerCase();
    return name.includes(q) || desc.includes(q) || catName.includes(q) || catCode.includes(q);
  }
  private matchesCategory(t: ContractTemplateResponse): boolean {
    if (!this.categoryFilter) return true;
    return this.getCategoryCode(t) === this.categoryFilter;
  }
  private matchesStatus(t: ContractTemplateResponse): boolean {
    if (!this.statusFilter) return true;
    return this.getStatusKey(t) === this.statusFilter;
  }

  get filteredTemplates(): ContractTemplateResponse[] {
    return this.templates.filter(t =>
      this.matchesSearch(t) && this.matchesCategory(t) && this.matchesStatus(t)
    );
  }

  // ===== Pagination =====
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
  get displayFrom(): number {
    return this.totalTemplates ? (this.currentPage - 1) * this.pageSize + 1 : 0;
  }
  get displayTo(): number {
    return Math.min(this.currentPage * this.pageSize, this.totalTemplates);
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

  // ===== Modal =====
  openDetail(t: ContractTemplateResponse): void {
    this.selectedTemplate = t;
    this.showDetailModal = true;
  }
  closeDetail(): void {
    this.showDetailModal = false;
    this.selectedTemplate = null;
  }

  
}
