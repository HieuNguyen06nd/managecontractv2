// src/app/home/contact-sign/contact-sign.component.ts
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';
import { forkJoin, of } from 'rxjs';
import { catchError, finalize, take } from 'rxjs/operators';

import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { ContractService } from '../../core/services/contract.service';
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

  // danh sách
  listLoading = false;
  errorMsg = '';

  // trạng thái theo item
  signing: Record<number, boolean> = {};
  approving: Record<number, boolean> = {};
  rejecting: Record<number, boolean> = {};

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
    this.refreshAll();
  }

  // ============ Fetch helpers ============
  private refreshAll() {
    this.listLoading = true;
    this.errorMsg = '';

    forkJoin({
      pending: this.service.getMyPendingContracts()
        .pipe(catchError(err => { this.toastApiError(err, 'Không tải được danh sách chờ phê duyệt.'); return of({ data: [] as ContractResponse[] }); })),
      approved: this.service.getMyHandledContracts('APPROVED')
        .pipe(catchError(err => { this.toastApiError(err, 'Không tải được danh sách đã duyệt.'); return of({ data: [] as ContractResponse[] }); })),
      rejected: this.service.getMyHandledContracts('REJECTED')
        .pipe(catchError(err => { this.toastApiError(err, 'Không tải được danh sách từ chối.'); return of({ data: [] as ContractResponse[] }); })),
    })
    .pipe(finalize(() => this.listLoading = false), take(1))
    .subscribe(({ pending, approved, rejected }: any) => {
      this.pendingContracts = (pending?.data ?? []).map((c: PendingCard) => ({
        ...c,
        currentStepName: c.currentStepName ?? 'Bước hiện tại',
        priority: c.priority ?? 'NORMAL'
      }));
      this.approvedContracts = (approved?.data ?? []);
      this.rejectedContracts = (rejected?.data ?? []);
    });
  }

  private toastApiError(err: any, fallback: string) {
    const msg = err?.error?.message || fallback;
    this.toastr.error(msg);
    this.errorMsg = msg;
  }

  // ============ Actions ============
  approve(contract: PendingCard) {
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }

    const id = contract.id!;
    this.approving[id] = true;

    const body: StepApprovalRequest = { comment: 'Đồng ý phê duyệt' };
    this.service.approveStep(id, contract.currentStepId, body)
      .pipe(finalize(() => this.approving[id] = false), take(1))
      .subscribe({
        next: () => {
          this.toastr.success('Phê duyệt hợp đồng thành công');
          this.closeModals();
          this.refreshAll();
          if (this.previewOpen && this.current?.id === id) this.reloadPreview();
        },
        error: (err) => this.toastApiError(err, 'Phê duyệt thất bại')
      });
  }

  reject(contract: PendingCard) {
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }
    if (!this.rejectReasonCode || !this.rejectComment.trim()) {
      this.toastr.info('Vui lòng chọn lý do và nhập mô tả chi tiết.');
      return;
    }

    const id = contract.id!;
    this.rejecting[id] = true;

    const body: StepApprovalRequest = { comment: `[${this.rejectReasonCode}] ${this.rejectComment}` };
    this.service.rejectStep(id, contract.currentStepId, body)
      .pipe(finalize(() => this.rejecting[id] = false), take(1))
      .subscribe({
        next: () => {
          this.toastr.success('Từ chối hợp đồng thành công');
          this.closeModals();
          this.refreshAll();
          if (this.previewOpen && this.current?.id === id) this.reloadPreview();
        },
        error: (err) => this.toastApiError(err, 'Từ chối thất bại')
      });
  }

  // ============ KÝ HỢP ĐỒNG ============
  sign(contract: PendingCard) {
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }

    const id = contract.id!;
    this.signing[id] = true;

    // Không cần placeholder nữa (BE tự ký theo tên + fallback)
    const body: SignStepRequest = { comment: null };

    this.service.signStep(id, contract.currentStepId, body)
      .pipe(finalize(() => this.signing[id] = false), take(1))
      .subscribe({
        next: () => {
          this.toastr.success('Ký hợp đồng thành công');
          this.refreshAll();
          if (this.previewOpen && this.current?.id === id) this.reloadPreview();
        },
        error: (err) => {
          const errorMsg = err?.error?.message || 'Ký thất bại';
          if (errorMsg.includes('chữ ký số')) this.toastr.error('Bạn chưa có chữ ký số. Vui lòng upload chữ ký trước.');
          else if (errorMsg.includes('quyền')) this.toastr.error('Bạn không có quyền ký bước này.');
          else if (errorMsg.includes('vị trí ký')) this.toastr.error('Không tìm thấy vị trí ký trong hợp đồng.');
          else this.toastr.error(errorMsg);
          console.error('Sign error:', err);
        }
      });
  }

  // ============ XEM TRƯỚC ============
  openPreview(contract: PendingCard) {
    this.current = contract;
    this.previewOpen = true;
    this.pdfError = null;
    this.pdfLoading = true;

    this.cacheBust = Date.now();
    this.previewDownloadUrl = this.contractService.buildPdfDownloadUrl(contract.id!, this.cacheBust);
    this.loadPdfBlob(contract.id!);
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

    if (this.pdfBlobUrl) { URL.revokeObjectURL(this.pdfBlobUrl); this.pdfBlobUrl = null; }

    this.cacheBust = Date.now();
    this.previewDownloadUrl = this.contractService.buildPdfDownloadUrl(this.current.id!, this.cacheBust);
    this.loadPdfBlob(this.current.id!);
  }

  private loadPdfBlob(contractId: number, tries = 0) {
    this.contractService.getContractPdfBlob(contractId, this.cacheBust)
      .pipe(take(1))
      .subscribe({
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

    const q = this.searchTerm.trim().toLowerCase();
    if (q) {
      list = list.filter(c =>
        (c.title ?? '').toLowerCase().includes(q) ||
        (c.contractNumber ?? '').toLowerCase().includes(q) ||
        (c.templateName ?? '').toLowerCase().includes(q)
      );
    }

    if (this.typeFilter) {
      list = list.filter(c => (c.templateName ?? '').toLowerCase().includes(this.typeFilter));
    }

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
    return this.isPending(c) && (c.currentStepAction === 'SIGN_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE') && !this.signing[c.id!];
  }

  canApproveVisible(c: PendingCard) {
    return this.isPending(c) && (c.currentStepAction === 'APPROVE_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE');
  }

  canReject(c: PendingCard) { return this.isPending(c); }

  // ============ MODALS ============
  openApprove(contract: PendingCard) { this.current = contract; this.approvalOpen = true; }
  openReject(contract: PendingCard)  {
    this.current = contract; this.rejectionOpen = true;
    this.rejectReasonCode = ''; this.rejectComment = ''; this.allowResubmission = true;
  }
  closeModals() { this.approvalOpen = false; this.rejectionOpen = false; }

  // ============ MISC ============
  formatDate(d?: string) { 
    if (!d) return ''; 
    try { return new Date(d).toLocaleDateString('vi-VN'); } catch { return d; } 
  }

  onTabChange(tab: FilterTab) { this.filterTab = tab; this.refreshAll(); }

  badgeClass(status?: string) {
    switch ((status || '').toUpperCase()) {
      case 'PENDING_APPROVAL': return 'badge-approval';
      case 'APPROVED':         return 'badge-success';
      case 'REJECTED':         return 'badge-danger';
      default:                 return 'badge-secondary';
    }
  }
}
