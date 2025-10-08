// src/app/home/contact-sign/contact-sign.component.ts
import {
  Component, OnInit, OnDestroy, AfterViewInit, inject, HostListener
} from '@angular/core';
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
  currentStepSigned?: boolean;
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
export class ContactSignComponent implements OnInit, OnDestroy, AfterViewInit {
  // ===== DI =====
  private service = inject(ContractApprovalService);
  private contractService = inject(ContractService);
  private toastr = inject(ToastrService);

  // ===== Data =====
  pendingContracts: PendingCard[] = [];
  approvedContracts: PendingCard[] = [];
  rejectedContracts: PendingCard[] = [];
  signaturePlaceholders: string[] = []; 

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

  // ===== Signature (drag on PDF) =====
  uploadedImageBase64: string | null = null;
  sigImgSrc: string | null = null;

  placingMode = false;
  sigGhostVisible = false;
  sig = { page: 1, left: 120, top: 120, width: 180, height: 60 }; // toạ độ HỆ CONTENT
  private drag = { active: false, offX: 0, offY: 0, resizing: false };

  // viewer rect + từng trang (tọa độ CONTENT)
  pdfHostRect: { left: number; top: number; width: number; height: number } | null = null;
  pageRects: Array<{
    page: number;
    left: number; top: number; width: number; height: number; // khung .page (content)
    cLeft: number; cTop: number; cWidth: number; cHeight: number; // vùng canvas thật
    scale: number; // hệ số scale của trang hiện tại
  }> = [];
  // PDF scroller thật
  private viewerScroller: HTMLElement | null = null;
  scroll = { left: 0, top: 0 };

  // ===== compatibility =====
  get placeMode() { return this.placingMode; }
  startPlaceSignature() { this.enablePlacingMode(); }

  // ============ Lifecycle ============
  ngOnInit(): void { this.fetchForTab('all'); }

  ngAfterViewInit(): void {
    window.addEventListener('resize', this.onWindowResize);
  }

  ngOnDestroy(): void {
    window.removeEventListener('resize', this.onWindowResize);
    this.detachScrollerAndListeners();
    if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
  }

  private onWindowResize = () => this.computePdfHostRectAndPages();

  // ============ Global drag listeners ============
  @HostListener('document:mousemove', ['$event'])
  onDocMouseMove(e: MouseEvent) { this.onDrag(e); this.onResize(e); }
  @HostListener('document:mouseup')
  onDocMouseUp() { this.endDrag(); this.endResize(); }

  // ============ Fetch ============
  private fetchForTab(tab: FilterTab) {
    if (tab === 'PENDING_APPROVAL' || tab === 'all') this.fetchPending();
    if (tab === 'APPROVED' || tab === 'all') this.fetchHandled('APPROVED');
    if (tab === 'REJECTED' || tab === 'all') this.fetchHandled('REJECTED');
  }

  fetchPending() {
    this.loading = true; this.errorMsg = '';
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
    if (!contract.currentStepId) { this.toastr.warning('Thiếu stepId của bước hiện tại.'); return; }
    if (contract.currentStepAction === 'SIGN_THEN_APPROVE' && !contract.currentStepSigned) {
      this.toastr.info('Bước này yêu cầu ký trước khi phê duyệt.'); return;
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

  // ============ Derived list ============
  get visibleContracts(): PendingCard[] {
    let list: PendingCard[] = [];
    if (this.filterTab === 'all') list = [...this.pendingContracts, ...this.approvedContracts, ...this.rejectedContracts];
    else if (this.filterTab === 'PENDING_APPROVAL') list = [...this.pendingContracts];
    else if (this.filterTab === 'APPROVED') list = [...this.approvedContracts];
    else list = [...this.rejectedContracts];

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

  isPending(c: PendingCard) { return ((c.status || '').toUpperCase() === 'PENDING_APPROVAL'); }
  canSign(c: PendingCard) { return this.isPending(c) && (c.currentStepAction === 'SIGN_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); }
  canApproveVisible(c: PendingCard) { return this.isPending(c) && (c.currentStepAction === 'APPROVE_ONLY' || c.currentStepAction === 'SIGN_THEN_APPROVE'); }
  approveDisabled(c: PendingCard) {
    if (!this.canApproveVisible(c)) return true;
    if (c.currentStepAction === 'SIGN_THEN_APPROVE') return !c.currentStepSigned;
    return false;
  }
  canReject(c: PendingCard) { return this.isPending(c); }

  // ============ Approve/Reject modals ============
  openApprove(contract: PendingCard) { this.current = contract; this.approvalOpen = true; }
  openReject(contract: PendingCard)  {
    this.current = contract; this.rejectionOpen = true;
    this.rejectReasonCode=''; this.rejectComment=''; this.allowResubmission=true;
  }
  closeModals() { this.approvalOpen = false; this.rejectionOpen = false; }

  // ============ Preview (viewer) ============
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
    if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
    this.pdfBlobUrl = null;
    this.previewDownloadUrl = null;

    this.placingMode = false;
    this.sigGhostVisible = false;

    this.detachScrollerAndListeners();
  }

  reloadPreview() {
    if (!this.current) return;
    this.pdfError = null;
    this.pdfLoading = true;
    if (this.pdfBlobUrl) { URL.revokeObjectURL(this.pdfBlobUrl); this.pdfBlobUrl = null; }
    this.cacheBust = Date.now();
    this.previewDownloadUrl = this.contractService.buildPdfDownloadUrl(this.current.id, this.cacheBust);
    this.loadPdfBlob(this.current.id);
  }

  private loadPdfBlob(contractId: number, tries = 0) {
    this.contractService.getContractPdfBlob(contractId, this.cacheBust).subscribe({
      next: (blob) => {
        this.pdfLoading = false;
        if (blob.type && !blob.type.toLowerCase().includes('pdf')) {
          this.pdfError = 'File trả về không phải PDF.'; return;
        }
        if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
        this.pdfBlobUrl = URL.createObjectURL(blob);

        setTimeout(() => {
          this.attachScrollerAndListeners();
          this.computePdfHostRectAndPages();
        }, 250);
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

  // ====== PDF scroller (viewerContainer) ======
  private attachScrollerAndListeners() {
    const comp = document.querySelector('ngx-extended-pdf-viewer') as any | null;
    if (!comp) return;
    const root: Document | ShadowRoot = (comp as any).shadowRoot ?? document;

    const scroller = root.querySelector('#viewerContainer') as HTMLElement | null;
    if (!scroller) return;

    this.viewerScroller = scroller;
    this.scroll.left = scroller.scrollLeft;
    this.scroll.top  = scroller.scrollTop;

    scroller.addEventListener('scroll', this.onPdfScroll, { passive: true });

    // Lắng nghe pageRendered/pagesLoaded ngay trong DOM thật
    root.addEventListener('pagerendered', this.onPdfReflow, { passive: true } as any);
    root.addEventListener('pagesloaded', this.onPdfReflow, { passive: true } as any);

    this.rebuildPageRects();
  }

private detachScrollerAndListeners() {
  const comp = document.querySelector('ngx-extended-pdf-viewer') as any | null;
  const root: Document | ShadowRoot = (comp as any)?.shadowRoot ?? document;

  if (this.viewerScroller) {
    this.viewerScroller.removeEventListener('scroll', this.onPdfScroll as any);
    this.viewerScroller = null;
  }
  root?.removeEventListener('pagerendered', this.onPdfReflow as any);
  root?.removeEventListener('pagesloaded', this.onPdfReflow as any);
}

private onPdfScroll = () => {
  if (!this.viewerScroller) return;
  this.scroll.left = this.viewerScroller.scrollLeft;
  this.scroll.top  = this.viewerScroller.scrollTop;
};

private onPdfReflow = () => {
  // mỗi lần render lại/zoom → rebuild
  this.rebuildPageRects();
};


  private rebuildPageRects() {
    if (!this.viewerScroller) return;

    const comp  = document.querySelector('ngx-extended-pdf-viewer') as any | null;
    const root: Document | ShadowRoot = (comp as any)?.shadowRoot ?? document;
    const viewer = root.querySelector('#viewer') as HTMLElement | null;
    if (!viewer) return;

    const pages = Array.from(viewer.querySelectorAll<HTMLElement>('.page'));
    const rects: typeof this.pageRects = [];

    for (const p of pages) {
      // scale: kích thước hiển thị / kích thước content không scale
      const pr = p.getBoundingClientRect();
      const unscaledW = p.offsetWidth || 1;
      const scale = pr.width / unscaledW;

      // .page trong hệ content
      const left   = p.offsetLeft;
      const top    = p.offsetTop;
      const width  = p.offsetWidth;
      const height = p.offsetHeight;

      // vùng canvas thật theo offset trong .page (=> hệ content)
      const cw = p.querySelector('.canvasWrapper') as HTMLElement | null;
      let cLeft = left, cTop = top, cWidth = width, cHeight = height;
      if (cw) {
        cLeft   = left + cw.offsetLeft;
        cTop    = top  + cw.offsetTop;
        cWidth  = cw.offsetWidth;
        cHeight = cw.offsetHeight;
      }

      rects.push({
        page: Number(p.getAttribute('data-page-number') ?? '0'),
        left, top, width, height,
        cLeft, cTop, cWidth, cHeight,
        scale
      });
    }

    this.pageRects = rects;

    // viewport của scroller – phục vụ căn giữa, trừ scroll khi vẽ ghost
    const hr = this.viewerScroller.getBoundingClientRect();
    this.pdfHostRect = { left: 0, top: 0, width: hr.width, height: hr.height };

    console.log('Sig Left:', this.sig.left);
    console.log('Sig Top:', this.sig.top);
    console.log('PDF Host Rect:', this.pdfHostRect);

  }



  // ============ Đo lại (fallback khi pageRendered) ============
computePdfHostRectAndPages() {
  if (this.viewerScroller) {
    this.rebuildPageRects();
  } else {
    this.attachScrollerAndListeners();
  }
}

  // ============ Signature: chuẩn bị ============
  onUploadSignatureFile(evt: Event) {
    const input = evt.target as HTMLInputElement | null;
    const f = input?.files?.[0];
    if (!f) return;
    if (!/image\/(png|jpeg|jpg)/i.test(f.type)) {
      this.toastr.warning('Vui lòng chọn ảnh PNG/JPG');
      return;
    }
    const r = new FileReader();
    r.onload = () => this.uploadedImageBase64 = r.result as string;
    r.readAsDataURL(f);
  }

enablePlacingMode() {
  if (!this.uploadedImageBase64) {
    this.toastr.info('Hãy tải ảnh chữ ký trước.');
    return;
  }
  this.sigImgSrc = this.uploadedImageBase64;
  this.placingMode = true;
  this.sigGhostVisible = true;

  setTimeout(() => {
    this.computePdfHostRectAndPages();
    if (this.pdfHostRect && this.viewerScroller) {
      // Căn giữa chữ ký trong hệ content
      const cx = (this.pdfHostRect.width / 2) + this.viewerScroller.scrollLeft;
      const cy = 40 + this.viewerScroller.scrollTop;  // Điều chỉnh lại giá trị này

      this.sig.left = cx - this.sig.width / 2;
      this.sig.top  = cy - this.sig.height / 2 + 20; // Điều chỉnh thêm nếu cần
    }
  }, 100);
}


  // ============ Signature: drag/resize ============
  startDrag(e: MouseEvent) {
    if (!this.sigGhostVisible || !this.viewerScroller) return;
    this.drag.active = true;

    const r = this.viewerScroller.getBoundingClientRect();
    const mouseX = e.clientX - r.left + this.viewerScroller.scrollLeft; // → hệ content
    const mouseY = e.clientY - r.top  + this.viewerScroller.scrollTop;
    this.drag.offX = mouseX - this.sig.left;
    this.drag.offY = mouseY - this.sig.top;

    e.preventDefault();
  }

  onDrag(e: MouseEvent) {
    if (!this.drag.active || this.drag.resizing || !this.viewerScroller) return;

    const r = this.viewerScroller.getBoundingClientRect();
    const mouseX = e.clientX - r.left + this.viewerScroller.scrollLeft;
    const mouseY = e.clientY - r.top  + this.viewerScroller.scrollTop;

    this.sig.left = mouseX - this.drag.offX;
    this.sig.top  = mouseY - this.drag.offY;
  }

  endDrag() {
    if (!this.drag.active || !this.viewerScroller) return;
    this.drag.active = false;

    const pad = 4;
    const maxL = this.viewerScroller.scrollWidth  - this.sig.width  - pad;
    const maxT = this.viewerScroller.scrollHeight - this.sig.height - pad;
    this.sig.left = Math.max(pad, Math.min(maxL, this.sig.left));
    this.sig.top  = Math.max(pad, Math.min(maxT, this.sig.top));
  }

  startResize(e: MouseEvent) { this.drag.resizing = true; e.stopPropagation(); }
  onResize(e: MouseEvent) {
    if (!this.drag.resizing) return;
    const minW = 80, minH = 30;
    this.sig.width  = Math.max(minW, this.sig.width  + e.movementX);
    this.sig.height = Math.max(minH, this.sig.height + e.movementY);
  }
  endResize() { this.drag.resizing = false; }

  // đổi từ px -> pt & xác định trang (tất cả ở hệ content)
 private buildSignPayloadFromGhost(): { page: number; x: number; y: number; width: number; height: number } | null {
  if (!this.pageRects.length) return null;

  // ghost trong HỆ CONTENT
  const gx1 = this.sig.left, gy1 = this.sig.top;
  const gx2 = gx1 + this.sig.width, gy2 = gy1 + this.sig.height;

  // chọn TRANG theo phần giao với vùng canvas thật (không dùng .page)
  let best: (typeof this.pageRects)[number] | null = null;
  let bestArea = 0;

  for (const p of this.pageRects) {
    const px1 = p.cLeft, py1 = p.cTop;
    const px2 = p.cLeft + p.cWidth, py2 = p.cTop + p.cHeight;

    const ix1 = Math.max(gx1, px1);
    const iy1 = Math.max(gy1, py1);
    const ix2 = Math.min(gx2, px2);
    const iy2 = Math.min(gy2, py2);

    const w = Math.max(0, ix2 - ix1);
    const h = Math.max(0, iy2 - iy1);
    const area = w * h;

    if (area > bestArea) { bestArea = area; best = p; }
  }

  if (!best || bestArea === 0) return null;

  this.sig.page = best.page;

  // Toạ độ tương đối với canvas (góc trái-trên canvas)
  const cssX = this.sig.left - best.cLeft;
  const cssY = this.sig.top - best.cTop;  // Điều chỉnh y theo canvas

  const cssW = this.sig.width;
  const cssH = this.sig.height;

  // px → pt, có tính scale: pt = (px / scale) * (72/96)
  const k = (72 / 96) / (best.scale || 1);

  const x_pt = Math.round(cssX * k);
  const y_pt = Math.round((best.cHeight - (cssY + cssH)) * k);  // Quy về gốc trái-dưới của trang

  // Điều chỉnh thêm vào Y để căn chính xác
  const adjustedY_pt = y_pt - 25;  // Thử thay đổi giá trị này để điều chỉnh vị trí chữ ký

  const w_pt = Math.round(cssW * k);
  const h_pt = Math.round(cssH * k);

  return { page: best.page, x: x_pt, y: adjustedY_pt, width: w_pt, height: h_pt };
}



  confirmDragSign() {
    if (!this.current || !this.current.currentStepId) {
      this.toastr.warning('Thiếu step hiện tại');
      return;
    }
    if (!this.sigImgSrc) {
      this.toastr.info('Chưa có ảnh chữ ký');
      return;
    }

    // Cập nhật lại rects mới nhất
    this.rebuildPageRects();

    const payload = this.buildSignPayloadFromGhost();
    if (!payload) {
      this.toastr.info('Hãy đặt chữ ký nằm trong một trang PDF');
      return;
    }

    const body: SignStepRequest = {
      imageBase64: this.sigImgSrc,
      comment: null,
      page: payload.page,
      x: payload.x, y: payload.y,
      width: payload.width, height: payload.height
    };

    this.loading = true;
    this.service.signStep(this.current.id, this.current.currentStepId!, body).subscribe({
      next: () => {
        this.toastr.success('Ký thành công');
        this.placingMode = false;
        this.sigGhostVisible = false;
        this.fetchPending();
        this.fetchHandled('APPROVED');
        this.reloadPreview();
      },
      error: (err) => this.toastr.error(err?.error?.message || 'Ký thất bại'),
      complete: () => this.loading = false
    });
  }


  openSign(contract: PendingCard) {
    this.openPreview(contract);
    setTimeout(() => {
      this.placingMode = true;
      this.sigGhostVisible = !!this.uploadedImageBase64;
      this.computePdfHostRectAndPages();
    }, 300);
  }

  // ============ Misc ============
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
