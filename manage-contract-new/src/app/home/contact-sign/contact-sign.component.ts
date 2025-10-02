import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HttpClient, HttpClientModule } from '@angular/common/http';
import { ToastrService } from 'ngx-toastr';
import { NgxExtendedPdfViewerModule } from 'ngx-extended-pdf-viewer';

import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { ResponseData } from '../../core/models/response-data.model';
import { StepApprovalRequest, ContractResponse, SignStepRequest } from '../../core/models/contract-approval.models';

type FilterTab = 'all' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
type SortBy = '' | 'newest' | 'oldest' | 'name';
type StepAction = 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';

interface PendingCard extends ContractResponse {
  currentStepId?: number;
  currentStepName?: string;
  currentStepAction?: StepAction;   // <-- mới
  currentStepSigned?: boolean;      // <-- khuyến nghị BE trả
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
  private sanitizer = inject(DomSanitizer);
  private toastr = inject(ToastrService);
  private http = inject(HttpClient);

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

  // ===== UI state =====
  approvalOpen = false;
  rejectionOpen = false;
  previewOpen = false;
  current: PendingCard | null = null;

  // Reject form
  rejectReasonCode = '';
  rejectComment = '';
  allowResubmission = true;

  // Preview
  pdfSrc: string | ArrayBuffer | Uint8Array | null = null;
  safePreviewUrl: SafeResourceUrl | null = null;

  // ==== Sign modal state ====
  signOpen = false;
  signMode: 'draw' | 'image' = 'draw';
  signComment = '';
  signPlaceholder = 'SIGN';
  coordUse = false;
  coord = { page: 1, x: 72, y: 72, w: 180, h: 60 };
  private drawCanvas?: HTMLCanvasElement;
  private drawCtx?: CanvasRenderingContext2D | null;
  private drawing = false;
  uploadedImageBase64: string | null = null;

  ngOnInit(): void {
    this.fetchForTab('all');
  }

  // ===== API =====
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
      error: (err) => {
        this.toastr.error(err?.error?.message || `Không tải được danh sách ${status === 'APPROVED' ? 'đã duyệt' : 'từ chối'}.`);
      }
    });
  }

  // ===== Actions =====
  approve(contract: PendingCard) {
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }

    // Nếu kiểu SIGN_THEN_APPROVE mà chưa ký → cảnh báo sớm (BE cũng sẽ chặn)
    if (contract.currentStepAction === 'SIGN_THEN_APPROVE' && !contract.currentStepSigned) {
      this.toastr.info('Bước này yêu cầu ký trước khi phê duyệt.');
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
      error: (err) => this.toastr.error(err?.error?.message || 'Phê duyệt thất bại'),
      complete: () => (this.loading = false)
    });
  }

  reject(contract: PendingCard) {
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }
    if (!this.rejectReasonCode || !this.rejectComment.trim()) {
      this.toastr.info('Vui lòng chọn lý do và nhập mô tả chi tiết.'); return;
    }
    const body: StepApprovalRequest = { comment: `[${this.rejectReasonCode}] ${this.rejectComment}` };
    this.loading = true;
    this.service.rejectStep(contract.id, contract.currentStepId, body).subscribe({
      next: () => {
        this.toastr.success('Từ chối hợp đồng thành công');
        this.closeModals();
        this.fetchPending();
        this.fetchHandled('REJECTED');
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Từ chối thất bại'),
      complete: () => (this.loading = false)
    });
  }

  // ===== Derived list =====
  get visibleContracts(): PendingCard[] {
    let list: PendingCard[] = [];
    if (this.filterTab === 'all') list = [...this.pendingContracts, ...this.approvedContracts, ...this.rejectedContracts];
    else if (this.filterTab === 'PENDING_APPROVAL') list = [...this.pendingContracts];
    else if (this.filterTab === 'APPROVED') list = [...this.approvedContracts];
    else if (this.filterTab === 'REJECTED') list = [...this.rejectedContracts];

    const q = this.searchTerm.trim().toLowerCase();
    if (q) list = list.filter(c =>
      (c.title ?? '').toLowerCase().includes(q) ||
      (c.contractNumber ?? '').toLowerCase().includes(q) ||
      (c.templateName ?? '').toLowerCase().includes(q)
    );

    if (this.typeFilter) list = list.filter(c => (c.templateName ?? '').toLowerCase().includes(this.typeFilter));

    if (this.sortBy === 'name') list.sort((a,b) => (a.title || '').localeCompare(b.title || ''));
    else if (this.sortBy === 'oldest') list.sort((a,b) => +new Date(a.createdAt || 0) - +new Date(b.createdAt || 0));
    else list.sort((a,b) => +new Date(b.createdAt || 0) - +new Date(a.createdAt || 0));

    return list;
  }

  trackById = (_: number, item: { id?: number }) => item?.id ?? _;

  // ===== Helpers: hiển thị theo action =====
  isPending(c: PendingCard) { return ((c.status || '').toUpperCase() === 'PENDING_APPROVAL'); }
  canSign(c: PendingCard) { return this.isPending(c) && (c.currentStepAction === 'SIGN_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); }
  canApproveVisible(c: PendingCard) { return this.isPending(c) && (c.currentStepAction === 'APPROVE_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); }
  approveDisabled(c: PendingCard) {
    if (!this.canApproveVisible(c)) return true;
    if (c.currentStepAction === 'SIGN_THEN_APPROVE') return !c.currentStepSigned; // nếu chưa có flag, BE sẽ chặn
    return false;
  }
  canReject(c: PendingCard) { return this.isPending(c); }

  // ===== Modal helpers =====
  openApprove(contract: PendingCard) { this.current = contract; this.approvalOpen = true; }
  openReject(contract: PendingCard)   { this.current = contract; this.rejectionOpen = true; this.rejectReasonCode=''; this.rejectComment=''; this.allowResubmission=true; }

  openPreview(contract: PendingCard) {
    this.current = contract;
    this.previewOpen = true;
    this.pdfSrc = null; this.safePreviewUrl = null;

    let path = contract.filePath || '';
    if (!path) { this.toastr.info('Hợp đồng chưa có file để xem trước.'); return; }
    if (path.startsWith('uploads/')) path = '/' + path;
    this.pdfSrc = path; // ngx-extended-pdf-viewer sẽ tải trực tiếp
  }

  closeModals() {
    this.approvalOpen = false;
    this.rejectionOpen = false;
    this.previewOpen = false;
    this.current = null;
    this.safePreviewUrl = null;
  }

  // ===== Sign popup =====
  openSign(contract: PendingCard) {
    this.current = contract;
    this.signOpen = true;
    this.signMode = 'draw';
    this.signComment = '';
    this.signPlaceholder = 'SIGN';
    this.coordUse = false;
    this.coord = { page: 1, x: 72, y: 72, w: 180, h: 60 };
    this.uploadedImageBase64 = null;
    setTimeout(() => this.initCanvas(), 0);
  }
  closeSign() {
    this.signOpen = false;
    this.uploadedImageBase64 = null;
    this.drawCanvas = undefined;
    this.drawCtx = null; this.drawing = false;
  }

  private initCanvas() {
    const el = document.getElementById('sig-canvas') as HTMLCanvasElement | null;
    if (!el) return;
    this.drawCanvas = el;
    el.width = el.clientWidth; el.height = 220;
    this.drawCtx = el.getContext('2d'); if (!this.drawCtx) return;
    this.drawCtx.lineWidth = 2; this.drawCtx.lineJoin='round'; this.drawCtx.lineCap='round'; this.drawCtx.strokeStyle='#111';

    const getXY = (ev: MouseEvent | TouchEvent) => {
      const rect = el.getBoundingClientRect();
      let clientX=0, clientY=0;
      if (ev instanceof TouchEvent) { const t = ev.touches[0] || ev.changedTouches[0]; clientX = t.clientX; clientY = t.clientY; }
      else { clientX = (ev as MouseEvent).clientX; clientY = (ev as MouseEvent).clientY; }
      return { x: clientX - rect.left, y: clientY - rect.top };
    };
    const start = (e:any)=>{ this.drawing=true; const p=getXY(e); this.drawCtx!.beginPath(); this.drawCtx!.moveTo(p.x,p.y); };
    const move  = (e:any)=>{ if(!this.drawing) return; const p=getXY(e); this.drawCtx!.lineTo(p.x,p.y); this.drawCtx!.stroke(); };
    const end   = ()=>{ this.drawing=false; };

    el.onmousedown=start; el.onmousemove=move; el.onmouseup=end; el.onmouseleave=end;
    el.ontouchstart=(e)=>{ e.preventDefault(); start(e); };
    el.ontouchmove =(e)=>{ e.preventDefault(); move(e);  };
    el.ontouchend  =(e)=>{ e.preventDefault(); end();    };
  }

  clearCanvas() {
    if (!this.drawCanvas || !this.drawCtx) return;
    this.drawCtx.clearRect(0,0,this.drawCanvas.width,this.drawCanvas.height);
  }

  onUploadImage(evt: Event) {
    const input = evt.target as HTMLInputElement | null;
    const files = input?.files;
    if (!files || !files[0]) return;

    const f = files[0];
    if (!/image\/(png|jpeg|jpg)/i.test(f.type)) {
      this.toastr.warning('Vui lòng chọn ảnh PNG/JPG');
      return;
    }
    const r = new FileReader();
    r.onload = () => this.uploadedImageBase64 = r.result as string;
    r.readAsDataURL(f);
  }


  confirmSign() {
    if (!this.current || !this.current.currentStepId) {
      this.toastr.warning('Thiếu stepId hiện tại.');
      return;
    }

    let imageBase64: string | null = null;
    if (this.signMode === 'draw') {
      if (!this.drawCanvas) { this.toastr.info('Canvas chưa sẵn sàng'); return; }
      imageBase64 = this.drawCanvas.toDataURL('image/png');
    } else {
      if (!this.uploadedImageBase64) { this.toastr.info('Vui lòng chọn ảnh chữ ký'); return; }
      imageBase64 = this.uploadedImageBase64;
    }

    const body: SignStepRequest = {
      imageBase64,
      comment: this.signComment || null,
      // Nếu không dùng toạ độ -> gửi placeholder
      ...(this.coordUse
        ? {
            // dùng toạ độ
            page: this.coord.page,
            x: this.coord.x,
            y: this.coord.y,
            width: this.coord.w,
            height: this.coord.h,
            // không gửi placeholder
          }
        : {
            // dùng placeholder
            placeholder: this.signPlaceholder || null,
          })
    };

    this.loading = true;
    this.service.signStep(this.current.id, this.current.currentStepId!, body).subscribe({
      next: () => {
        this.toastr.success('Ký thành công');
        this.closeSign();
        this.closeModals();
        this.fetchPending();
        this.fetchHandled('APPROVED');
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Ký thất bại'),
      complete: () => this.loading = false
    });
  }


  // ===== Misc =====
  formatDate(d?: string) { if (!d) return ''; try { return new Date(d).toLocaleDateString('vi-VN'); } catch { return d; } }
  onTabChange(tab: FilterTab) { this.filterTab = tab; this.fetchForTab(tab); }

  badgeClass(status?: string) {
    switch ((status || '').toUpperCase()) {
      case 'PENDING_APPROVAL': return 'badge-approval';
      case 'APPROVED':         return 'badge-success';
      case 'REJECTED':         return 'badge-danger';
      default:                 return 'badge-secondary';
    }
  }
}
