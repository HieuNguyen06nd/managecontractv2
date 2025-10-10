// src/app/home/contact-sign/contact-sign.component.ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';

import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { ContractService } from '../../core/services/contract.service';
import { ResponseData } from '../../core/models/response-data.model';
import { StepApprovalRequest, SignStepRequest } from '../../core/models/contract-approval.models';
import { ContractResponse } from '../../core/models/contract.model';

type FilterTab = 'all' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
type SortBy = '' | 'newest' | 'oldest' | 'name';
type StepAction = 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';

interface PendingCard extends ContractResponse {
  currentStepId?: number;
  currentStepName?: string;
  currentStepAction?: StepAction;
  currentStepSignaturePlaceholder?: string;
  priority?: 'HIGH' | 'NORMAL';
  createdAt?: string;
}

@Component({
  selector: 'app-contact-sign',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule, NgxExtendedPdfViewerModule],
  templateUrl: './contact-sign.component.html',
  styleUrls: ['./contact-sign.component.scss']
})
export class ContactSignComponent implements OnInit {
  // ===== DI =====
  private service = inject(ContractApprovalService);
  private contractService = inject(ContractService);
  private toastr = inject(ToastrService);

  // ===== Data =====
  pendingContracts: PendingCard[] = [];
  approvedContracts: PendingCard[] = [];
  rejectedContracts: PendingCard[] = [];

  loading = false;
  errorMsg = '';

  // ===== Filters / Sort / Search =====
  filterTab: FilterTab = 'all';
  typeFilter: '' | 'labor' | 'sale' | 'rental' | 'service' = '';
  searchTerm = '';
  sortBy: SortBy = 'newest';

  // ===== Approve / Reject modal state =====
  approvalOpen = false;
  rejectionOpen = false;
  rejectReasonCode = '';
  rejectComment = '';
  allowResubmission = true;

  // ===== Preview (viewer) =====
  previewOpen = false;
  current: PendingCard | null = null;
  pdfLoading = false;
  pdfError: string | null = null;
  pdfBlobUrl: string | null = null;
  previewDownloadUrl: string | null = null;
  private cacheBust = Date.now();

  ngOnInit(): void { 
    this.fetchForTab('all'); 
  }

  // ============ Fetch ============
  private fetchForTab(tab: FilterTab) {
    if (tab === 'PENDING_APPROVAL' || tab === 'all') this.fetchPending();
    if (tab === 'APPROVED' || tab === 'all') this.fetchHandled('APPROVED');
    if (tab === 'REJECTED' || tab === 'all') this.fetchHandled('REJECTED');
  }

  fetchPending() {
    this.loading = true; 
    this.errorMsg = '';
    
    this.service.getMyPendingContracts().subscribe({
      next: (res: ResponseData<ContractResponse[]>) => {
        const raw = (res.data ?? []) as PendingCard[];
        this.pendingContracts = raw.map(c => ({
          ...c,
          currentStepName: c.currentStepName ?? 'Bước hiện tại',
          priority: c.priority ?? 'NORMAL'
        }));
      },
      error: (err) => {
        this.errorMsg = err?.error?.message || 'Không tải được danh sách chờ phê duyệt.';
        this.toastr.error(this.errorMsg);
      },
      complete: () => (this.loading = false)
    });
  }

  private fetchHandled(status: 'APPROVED' | 'REJECTED') {
    this.service.getMyHandledContracts(status).subscribe({
      next: (res: ResponseData<ContractResponse[]>) => {
        const list = (res.data ?? []) as PendingCard[];
        if (status === 'APPROVED') this.approvedContracts = list;
        else this.rejectedContracts = list;
      },
      error: (err) =>
        this.toastr.error(err?.error?.message || `Không tải được danh sách ${status === 'APPROVED' ? 'đã duyệt' : 'từ chối'}.`)
    });
  }

  // ============ Actions ============
  approve(contract: PendingCard) {
    if (!contract.currentStepId) { 
      this.toastr.warning('Thiếu stepId của bước hiện tại.'); 
      return; 
    }

    const body: StepApprovalRequest = { comment: 'Đồng ý phê duyệt' };
    this.loading = true;
    
    this.service.approveStep(contract.id, contract.currentStepId, body).subscribe({
      next: () => {
        this.toastr.success('Phê duyệt hợp đồng thành công');
        this.closeModals();
        this.fetchPending();
        this.fetchHandled('APPROVED');
      },
      error: (err) => {
        this.toastr.error(err?.error?.message || 'Phê duyệt thất bại');
        this.loading = false;
      },
      complete: () => (this.loading = false)
    });
  }

  reject(contract: PendingCard) {
    if (!contract.currentStepId) { 
      this.toastr.warning('Thiếu stepId của bước hiện tại.'); 
      return; 
    }
    
    if (!this.rejectReasonCode || !this.rejectComment.trim()) {
      this.toastr.info('Vui lòng chọn lý do và nhập mô tả chi tiết.'); 
      return;
    }
    
    const body: StepApprovalRequest = { 
      comment: `[${this.rejectReasonCode}] ${this.rejectComment}` 
    };
    
    this.loading = true;
    this.service.rejectStep(contract.id, contract.currentStepId, body).subscribe({
      next: () => {
        this.toastr.success('Từ chối hợp đồng thành công');
        this.closeModals();
        this.fetchPending();
        this.fetchHandled('REJECTED');
      },
      error: (err) => {
        this.toastr.error(err?.error?.message || 'Từ chối thất bại');
        this.loading = false;
      },
      complete: () => (this.loading = false)
    });
  }

  // ============ KÝ HỢP ĐỒNG ============
  sign(contract: PendingCard) {
    if (!contract.currentStepId) {
      this.toastr.warning('Thiếu stepId của bước hiện tại.');
      return;
    }

    if (!contract.currentStepSignaturePlaceholder) {
      this.toastr.info('Bước này chưa được cấu hình vị trí ký.');
      return;
    }

    const body: SignStepRequest = {
      placeholder: contract.currentStepSignaturePlaceholder,
      comment: null
    };

    this.loading = true;
    this.service.signStep(contract.id, contract.currentStepId, body).subscribe({
      next: () => {
        this.toastr.success('Ký thành công');
        this.fetchPending();
        this.fetchHandled('APPROVED');
        
        // Reload preview nếu đang mở
        if (this.previewOpen && this.current?.id === contract.id) {
          this.reloadPreview();
        }
      },
      error: (err) => {
        this.toastr.error(err?.error?.message || 'Ký thất bại');
        this.loading = false;
      },
      complete: () => (this.loading = false)
    });
  }

  // ============ XEM TRƯỚC ============
  openPreview(contract: PendingCard) {
    this.current = contract;
    this.previewOpen = true;
    this.pdfError = null;
    this.pdfLoading = true;

    this.cacheBust = Date.now();
    this.previewDownloadUrl = this.contractService.buildPdfDownloadUrl(contract.id, this.cacheBust);
    this.loadPdfBlob(contract.id);
  }

  closePreview() {
    this.previewOpen = false;
    this.current = null;
    this.pdfError = null;
    this.pdfLoading = false;
    
    if (this.pdfBlobUrl) {
      URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
    }
    
    this.previewDownloadUrl = null;
  }

  reloadPreview() {
    if (!this.current) return;
    
    this.pdfError = null;
    this.pdfLoading = true;
    
    if (this.pdfBlobUrl) { 
      URL.revokeObjectURL(this.pdfBlobUrl); 
      this.pdfBlobUrl = null; 
    }
    
    this.cacheBust = Date.now();
    this.previewDownloadUrl = this.contractService.buildPdfDownloadUrl(this.current.id, this.cacheBust);
    this.loadPdfBlob(this.current.id);
  }

  private loadPdfBlob(contractId: number, tries = 0) {
    this.contractService.getContractPdfBlob(contractId, this.cacheBust).subscribe({
      next: (blob) => {
        this.pdfLoading = false;
        if (blob.type && !blob.type.toLowerCase().includes('pdf')) {
          this.pdfError = 'File trả về không phải PDF.'; 
          return;
        }
        
        if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
        this.pdfBlobUrl = URL.createObjectURL(blob);
      },
      error: (err) => {
        const retriable = [404,409,423,425,429,500,502,503,504].includes(err?.status);
        if (retriable && tries < 5) {
          setTimeout(() => this.loadPdfBlob(contractId, tries + 1), 800 * (tries + 1));
        } else {
          this.pdfLoading = false;
          this.pdfError = this.readablePdfError(err);
        }
      }
    });
  }

  private readablePdfError(err: any): string {
    const st = err?.status;
    if (st === 401 || st === 403) return 'Bạn không có quyền xem file này.';
    if (st === 404) return 'File chưa sẵn sàng hoặc không tồn tại. Thử lại sau ít giây.';
    if ([502,503,504].includes(st)) return 'Máy chuyển đổi đang bận. Hãy thử lại.';
    return 'Không xem được PDF. Vui lòng thử lại.';
  }

  // ============ UTILITIES ============
  get visibleContracts(): PendingCard[] {
    let list: PendingCard[] = [];
    
    if (this.filterTab === 'all') list = [...this.pendingContracts, ...this.approvedContracts, ...this.rejectedContracts];
    else if (this.filterTab === 'PENDING_APPROVAL') list = [...this.pendingContracts];
    else if (this.filterTab === 'APPROVED') list = [...this.approvedContracts];
    else list = [...this.rejectedContracts];

    // Filter by search
    const q = this.searchTerm.trim().toLowerCase();
    if (q) {
      list = list.filter(c =>
        (c.title ?? '').toLowerCase().includes(q) ||
        (c.contractNumber ?? '').toLowerCase().includes(q) ||
        (c.templateName ?? '').toLowerCase().includes(q)
      );
    }

    // Filter by type
    if (this.typeFilter) {
      list = list.filter(c => (c.templateName ?? '').toLowerCase().includes(this.typeFilter));
    }

    // Sort
    if (this.sortBy === 'name') {
      list.sort((a,b) => (a.title || '').localeCompare(b.title || ''));
    } else if (this.sortBy === 'oldest') {
      list.sort((a,b) => +new Date(a.createdAt || 0) - +new Date(b.createdAt || 0));
    } else {
      list.sort((a,b) => +new Date(b.createdAt || 0) - +new Date(a.createdAt || 0));
    }
    
    return list;
  }

  trackById = (_: number, item: { id?: number }) => item?.id ?? _;

  isPending(c: PendingCard) { 
    return ((c.status || '').toUpperCase() === 'PENDING_APPROVAL'); 
  }
  
  canSign(c: PendingCard) { 
    return this.isPending(c) && 
           (c.currentStepAction === 'SIGN_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); 
  }
  
  canApproveVisible(c: PendingCard) { 
    return this.isPending(c) && 
           (c.currentStepAction === 'APPROVE_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); 
  }
  
  canReject(c: PendingCard) { 
    return this.isPending(c); 
  }

  // ============ MODALS ============
  openApprove(contract: PendingCard) { 
    this.current = contract; 
    this.approvalOpen = true; 
  }
  
  openReject(contract: PendingCard)  {
    this.current = contract; 
    this.rejectionOpen = true;
    this.rejectReasonCode = ''; 
    this.rejectComment = ''; 
    this.allowResubmission = true;
  }
  
  closeModals() { 
    this.approvalOpen = false; 
    this.rejectionOpen = false; 
  }

  // ============ MISC ============
  formatDate(d?: string) { 
    if (!d) return ''; 
    try { 
      return new Date(d).toLocaleDateString('vi-VN'); 
    } catch { 
      return d; 
    } 
  }
  
  onTabChange(tab: FilterTab) { 
    this.filterTab = tab; 
    this.fetchForTab(tab); 
  }
  
  badgeClass(status?: string) {
    switch ((status || '').toUpperCase()) {
      case 'PENDING_APPROVAL': return 'badge-approval';
      case 'APPROVED':         return 'badge-success';
      case 'REJECTED':         return 'badge-danger';
      default:                 return 'badge-secondary';
    }
  }
}