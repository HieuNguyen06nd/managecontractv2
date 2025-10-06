import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ContractService } from '../../core/services/contract.service';
import { ContractResponse } from '../../core/models/contract.model';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { timer } from 'rxjs';
import { retryWhen, scan, delayWhen } from 'rxjs/operators';
import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { ApprovalFlowService, ApprovalFlowResponse, ApprovalStepResponse } from '../../core/services/contract-flow.service';

type UiStatus = 'all' | 'draft' | 'pending' | 'signed' | 'cancelled';

@Component({
  selector: 'app-contract-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './contract-list.component.html',
  styleUrls: ['./contract-list.component.scss']
})
export class ContractListComponent implements OnInit {
  private contractService = inject(ContractService);
  private approvalService = inject(ContractApprovalService);
  private flowService = inject(ApprovalFlowService);
  private sanitizer = inject(DomSanitizer);

  public math = Math;

  // data
  contracts = signal<ContractResponse[]>([]);
  loading   = signal<boolean>(false);
  error     = signal<string | null>(null);

  // filters
  statusFilter = signal<UiStatus>('all');
  keyword      = signal<string>('');
  dateFrom     = signal<string>('');
  dateTo       = signal<string>('');

  // pagination
  pageIndex = signal<number>(1);
  pageSize  = signal<number>(10);

  // PDF modal
  showPdf = signal<boolean>(false);
  pdfBlobUrl: string | null = null;
  pdfSafeUrl: SafeResourceUrl | null = null;
  pdfLoading = signal<boolean>(false);
  pdfError = signal<string | null>(null);

  // Submit modal
  showSubmit = signal<boolean>(false);
  submitting = signal<boolean>(false);
  submitError = signal<string | null>(null);
  submitTarget: ContractResponse | null = null;

  // Flow selector + viewer
  flows = signal<ApprovalFlowResponse[]>([]);
  flowsLoading = signal<boolean>(false);
  selectedFlowId = signal<number | null>(null);

  // Flow hiện trạng (nếu HĐ đã gắn)
  currentFlow = signal<{
    exists: boolean;
    steps: Array<{
      id: number;
      stepOrder: number;
      approverName?: string;
      action?: string;
      required?: boolean;
      placeholderKey?: string;
      status?: string;
      decidedBy?: string;
      decidedAt?: string;
    }>;
  } | null>(null);


  // derived
  filtered = computed(() => {
    let data = this.contracts();

    const kw = this.keyword().trim().toLowerCase();
    if (kw) {
      data = data.filter(x =>
        (x.contractNumber?.toLowerCase().includes(kw)) ||
        (x.title?.toLowerCase().includes(kw))
      );
    }

    const st = this.statusFilter();
    if (st !== 'all') {
      data = data.filter(x => {
        const s = (x.status || '').toUpperCase();
        switch (st) {
          case 'draft':     return s === 'DRAFT';
          case 'pending':   return s === 'PENDING_APPROVAL';
          case 'signed':    return s === 'APPROVED';
          case 'cancelled': return s === 'REJECTED' || s === 'CANCELLED';
        }
      });
    }

    const from = this.dateFrom() ? new Date(this.dateFrom()) : null;
    const to   = this.dateTo()   ? new Date(this.dateTo())   : null;
    if (from || to) {
      data = data.filter((x: any) => {
        const createdAtRaw = x.createdAt ?? x.created_date ?? x.createdTime;
        if (!createdAtRaw) return false;
        const createdAt = new Date(createdAtRaw);
        if (Number.isNaN(createdAt.getTime())) return false;
        if (from && createdAt < from) return false;
        if (to) {
          const end = new Date(to);
          end.setHours(23,59,59,999);
          if (createdAt > end) return false;
        }
        return true;
      });
    }

    return data;
  });

  pageCount = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize())));
  paged = computed(() => {
    const page = this.pageIndex();
    const size = this.pageSize();
    const start = (page - 1) * size;
    return this.filtered().slice(start, start + size);
  });

  // stats
  statDraft     = computed(() => this.contracts().filter(x => (x.status || '').toUpperCase() === 'DRAFT').length);
  statPending   = computed(() => this.contracts().filter(x => (x.status || '').toUpperCase() === 'PENDING_APPROVAL').length);
  statSigned    = computed(() => this.contracts().filter(x => (x.status || '').toUpperCase() === 'APPROVED').length);
  statCancelled = computed(() => this.contracts().filter(x => ['REJECTED','CANCELLED'].includes((x.status || '').toUpperCase())).length);

  ngOnInit(): void { this.fetchContracts(); }

  // ===== data =====
  fetchContracts(): void {
    this.loading.set(true);
    this.error.set(null);

    this.contractService.getMyContracts().subscribe({
      next: (res) => {
        this.contracts.set(res.data || []);
        this.loading.set(false);
        this.pageIndex.set(1);
      },
      error: () => {
        this.error.set('Không tải được danh sách hợp đồng');
        this.loading.set(false);
      }
    });
  }

  // ===== filters/paging =====
  resetFilters(): void {
    this.statusFilter.set('all');
    this.keyword.set('');
    this.dateFrom.set('');
    this.dateTo.set('');
    this.pageIndex.set(1);
  }
  search(): void { this.pageIndex.set(1); }
  goPage(p: number): void {
    const max = this.pageCount();
    if (p < 1 || p > max) return;
    this.pageIndex.set(p);
  }

  // ===== actions: create/edit/remove =====
  createContract(): void { alert('Đi đến màn tạo hợp đồng'); }
  edit(item: ContractResponse): void { alert(`Chỉnh sửa: ${item.contractNumber || item.id}`); }
  remove(item: ContractResponse): void { alert(`Xóa: ${item.contractNumber || item.id}`); }

  // ===== PDF Viewer (popup) =====
  viewPdf(item: ContractResponse): void {
    this.pdfError.set(null);
    this.pdfLoading.set(true);

    this.contractService.getContractPdfBlob(item.id).subscribe({
      next: (blob) => {
        if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
        this.pdfBlobUrl = URL.createObjectURL(blob);
        this.pdfSafeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfBlobUrl);
        this.pdfLoading.set(false);
        this.showPdf.set(true);
      },
      error: (err) => {
        console.error(err);
        this.pdfLoading.set(false);
        this.pdfError.set(this.readablePdfError(err));
        this.showPdf.set(true);
      }
    });
  }
  downloadFromModal(name = 'contract.pdf'): void {
    if (!this.pdfBlobUrl) return;
    const a = document.createElement('a');
    a.href = this.pdfBlobUrl;
    a.download = name;
    a.click();
  }
  closePdf(): void {
    this.showPdf.set(false);
    if (this.pdfBlobUrl) {
      URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
      this.pdfSafeUrl = null;
    }
    this.pdfError.set(null);
  }
  private readablePdfError(err: any): string {
    const st = err?.status;
    if (st === 401 || st === 403) return 'Bạn không có quyền xem file này.';
    if (st === 404) return 'File chưa sẵn sàng hoặc không tồn tại. Thử lại sau ít giây.';
    if (st === 502 || st === 503 || st === 504) return 'Máy chuyển đổi đang bận. Hãy thử lại.';
    return 'Không xem được PDF. Vui lòng thử lại.';
  }
  private getPdfWithRetry(id: number, maxRetries = 6, intervalMs = 1000, isDownload = false) {
    const call$ = isDownload
      ? this.contractService.downloadContractPdfBlob(id)
      : this.contractService.getContractPdfBlob(id);

    return call$.pipe(
      retryWhen(errors =>
        errors.pipe(
          scan((acc, err) => {
            const retriable = [404, 409, 423, 425, 429, 500, 502, 503, 504].includes(err?.status);
            if (!retriable) throw err;
            if (acc >= maxRetries - 1) throw err;
            return acc + 1;
          }, 0),
          delayWhen(retryIndex => timer(intervalMs * (retryIndex + 1)))
        )
      )
    );
  }

  
  // ===== Submit (chọn/hiện flow) =====
  openSubmitModal(item: ContractResponse) {
    this.submitTarget = item;
    this.submitError.set(null);
    this.selectedFlowId.set(null);
    this.flows.set([]);
    this.flowsLoading.set(false);
    this.currentFlow.set(null);

    // gọi preview (BE sẽ tự quyết định: progress hay preview)
    this.approvalService.getPreview(item.id).subscribe({
      next: res => {
        const d = res.data as any; // { hasFlow:boolean, steps:[...] }
        this.currentFlow.set({
          exists: !!d.hasFlow,  // <-- chỉ true khi đã submit
          steps: (d.steps || []).map((s: any) => ({
            id: s.id,
            stepOrder: s.stepOrder,
            approverName: s.approverName,
            action: s.action,
            required: s.required,
            placeholderKey: s.signaturePlaceholder,
            status: s.status,          // chỉ có khi hasFlow=true
            decidedBy: s.decidedBy,    // chỉ có khi hasFlow=true
            decidedAt: s.decidedAt     // chỉ có khi hasFlow=true
          }))
        });

        // Nếu CHƯA có flow gắn → cho phép chọn flow để submit
        if (!d.hasFlow && item.templateId) {
          this.flowsLoading.set(true);
          this.flowService.listFlowsByTemplate(item.templateId).subscribe({
            next: r => {
              this.flows.set(r.data || []);
              if (this.flows().length > 0) this.selectedFlowId.set(this.flows()[0].id);
              this.flowsLoading.set(false);
              this.showSubmit.set(true);
            },
            error: () => { this.flowsLoading.set(false); this.showSubmit.set(true); }
          });
        } else {
          // đã có flow → chỉ xem, không submit lại
          this.showSubmit.set(true);
        }
      },
      error: () => {
        // fallback: không lấy được preview vẫn cho chọn flow (nếu có templateId)
        if (item.templateId) {
          this.flowsLoading.set(true);
          this.flowService.listFlowsByTemplate(item.templateId).subscribe({
            next: r => { this.flows.set(r.data || []); if (this.flows().length) this.selectedFlowId.set(this.flows()[0].id); this.flowsLoading.set(false); this.showSubmit.set(true); },
            error: () => { this.flowsLoading.set(false); this.showSubmit.set(true); }
          });
        } else {
          this.showSubmit.set(true);
        }
      }
    });
  }



  closeSubmitModal() {
    this.showSubmit.set(false);
    this.submitTarget = null;
    this.submitError.set(null);
    this.selectedFlowId.set(null);
    this.flows.set([]);
    this.currentFlow.set(null);
  }

  onChangeFlow(flowId: number | null) {
    this.selectedFlowId.set(flowId);
    // có thể preview plannedFlow tại đây nếu muốn
  }

  confirmSubmit() {
    if (!this.submitTarget) return;

    // nếu đã có flow -> không gọi submit nữa
    if (this.currentFlow()?.exists) {
      this.submitError.set('Hợp đồng đã đang trong quy trình phê duyệt.');
      return;
    }

    const flowId = this.selectedFlowId() ?? undefined;
    this.submitting.set(true);
    this.submitError.set(null);

    this.approvalService.submitForApproval(this.submitTarget.id, flowId).subscribe({
      next: (res) => {
        // merge lại item (tránh lỗi type khi filePath có null)
        const updated = { ...res.data, filePath: (res.data as any)?.filePath ?? undefined } as ContractResponse;
        this.contracts.update(list => list.map(x => x.id === updated.id ? { ...x, ...updated } : x));
        this.submitting.set(false);
        this.showSubmit.set(false);
        alert('Đã trình ký thành công!');
      },
      error: (err) => {
        if (err?.status === 409 || (err?.error?.message || '').includes('already has an approval flow')) {
          this.submitError.set('Hợp đồng đã có luồng trình ký. Mở “Tiến trình ký” để theo dõi.');
          // refresh progress
          this.approvalService.getApprovalProgress(this.submitTarget!.id).subscribe(p => {
            const steps = (p.data as any)?.steps || [];
            this.currentFlow.set({ exists: steps.length > 0, steps });
          });
        } else {
          this.submitError.set('Trình ký thất bại. Vui lòng thử lại.');
        }
        this.submitting.set(false);
      }
    });
  }

  goTrackFlow() {
    if (!this.submitTarget) return;
    alert(`Đi đến theo dõi quy trình hợp đồng #${this.submitTarget.id}`);
  }

  // ===== UI helpers =====
  statusBadgeClass(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'DRAFT') return 'status-badge status-draft';
    if (s === 'PENDING_APPROVAL') return 'status-badge status-pending';
    if (s === 'APPROVED') return 'status-badge status-signed';
    if (s === 'REJECTED' || s === 'CANCELLED') return 'status-badge status-cancelled';
    return 'status-badge';
  }
  statusLabel(status?: string): string {
    const s = (status || '').toUpperCase();
    if (s === 'DRAFT') return 'Nháp';
    if (s === 'PENDING_APPROVAL') return 'Đã trình ký';
    if (s === 'APPROVED') return 'Đã ký';
    if (s === 'REJECTED' || s === 'CANCELLED') return 'Đã hủy';
    return s || '-';
  }
  createdAt(row: any): string {
    const v = row?.createdAt ?? row?.created_date ?? row?.createdTime;
    return this.fmtDate(v);
  }
  fmtDate(d?: any): string {
    if (!d) return '-';
    const dt = new Date(d);
    return isNaN(dt.getTime()) ? '-' : dt.toLocaleDateString('vi-VN');
  }
  createdByName(row: any): string {
    return row.createdByName || row.createdBy?.fullName || '-';
  }

  // labels for flow viewer
  private actionLabel(a?: string) {
    switch ((a || '').toUpperCase()) {
      case 'SIGN_ONLY': return 'Ký';
      case 'APPROVE_ONLY': return 'Phê duyệt';
      case 'SIGN_THEN_APPROVE': return 'Ký rồi phê duyệt';
      default: return 'Xử lý';
    }
  }
  stepToText(s: any) {
    const name = s.approverName || '—';
    const act  = this.actionLabel(s.action);
    const opt  = s.required ? '' : ' [không bắt buộc]';
    const slot = s.placeholderKey ? ` — ô ký: ${s.placeholderKey}` : '';
    return `${name} (${act})${opt}${slot}`;
  }
}
