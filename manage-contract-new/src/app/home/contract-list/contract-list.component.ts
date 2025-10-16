import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { ContractService } from '../../core/services/contract.service';
import { ContractResponse, VariableValueResponse } from '../../core/models/contract.model';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { timer } from 'rxjs';
import { Router } from '@angular/router';  
import { retryWhen, scan, delayWhen } from 'rxjs/operators';
import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { ApprovalFlowService, ApprovalFlowResponse, ApprovalStepResponse } from '../../core/services/contract-flow.service';
import { FormBuilder, FormGroup } from '@angular/forms'; 
import { ResponseData } from '../../core/models/response-data.model';
import { PlannedFlowResponse } from '../../core/models/contrac-flow.model';


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
  private fb = inject(FormBuilder);

  public math = Math;

  constructor(
    private router: Router
  ) {}

  plannedFlow = signal<PlannedFlowResponse | null>(null);


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

  showDeleteModal = signal<boolean>(false);
  showCancelModal = signal<boolean>(false);
  deleteContract: ContractResponse | null = null;
  cancelContract: ContractResponse | null = null;

  attachedFlowId = signal<number | null>(null);        // flow đang gắn với hợp đồng (khi DRAFT)
  attachedFlow   = signal<ApprovalFlowResponse | null>(null);
  updatingFlow   = signal<boolean>(false);
  updateFlowError = signal<string | null>(null);

  defaultFlow = signal<ApprovalFlowResponse | null>(null);
  defaultFlowLoading = signal<boolean>(false);


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

  goToCreateTemplatePage(): void {
    this.router.navigate(['/contract/create']);
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

  openSubmitModal(item: ContractResponse) {
    this.submitTarget = item;
    this.submitError.set(null);

    // reset state
    this.selectedFlowId.set(null);
    this.flows.set([]);
    this.flowsLoading.set(false);
    this.currentFlow.set(null);
    this.attachedFlowId.set(null);
    this.attachedFlow.set(null);
    this.defaultFlow.set(null);
    this.defaultFlowLoading.set(false);
    this.plannedFlow.set(null);

    // 1) Luôn lấy chi tiết HĐ trước
    this.contractService.getContractById(item.id).subscribe({
      next: (res) => {
        const d = res.data as ContractResponse;
        const status = (d?.status || item.status || '').toUpperCase();

        // Nếu đã trình ký -> hiển thị tiến trình runtime
        if (status === 'PENDING_APPROVAL') {
          const steps = this.sortSteps((d as any)?.steps || []); // nếu BE trả kèm steps runtime
          this.currentFlow.set({
            exists: true,
            steps: steps.map((s: any) => ({
              id: s.id,
              stepOrder: s.stepOrder,
              approverName: s.approverName,
              action: s.action,
              required: s.required,
              placeholderKey: s.signaturePlaceholder,
              status: s.status,
              decidedBy: s.decidedBy,
              decidedAt: s.decidedAt
            }))
          });
          this.showSubmit.set(true);
          return;
        }

        // 2) DRAFT: gọi planned-flow để biết "luồng hiện tại" (nếu HĐ đã gắn flowId)
        this.contractService.getPlannedFlow(item.id).subscribe({
          next: (pfRes) => {
            const pf = pfRes.data;
            // sort step theo stepOrder
            pf.steps = this.sortSteps(pf.steps);
            this.plannedFlow.set(pf);

            // Nếu planned-flow có flowId -> coi như "luồng đang gắn"
            if (pf.flowId) {
              this.attachedFlowId.set(pf.flowId);
              this.selectedFlowId.set(pf.flowId); // preselect
            }

            // 3) Luôn nạp default + full list flow theo template để user có thể đổi
            if (d.templateId) {
              this.loadDefaultFlowThenFlows(d.templateId);
            }

            this.showSubmit.set(true);
          },
          error: () => {
            // Không có planned-flow vẫn cho chọn flow theo template
            if (d.templateId) {
              this.loadDefaultFlowThenFlows(d.templateId);
            }
            this.showSubmit.set(true);
          }
        });
      },
      error: () => {
        // fallback
        if (item.templateId) this.loadDefaultFlowThenFlows(item.templateId);
        this.showSubmit.set(true);
      }
    });
  }



  saveFlowChangeOnly() {
    if (!this.submitTarget) return;
    const newFlowId = this.selectedFlowId();
    if (!newFlowId) {
      this.updateFlowError.set('Vui lòng chọn luồng.');
      return;
    }
    if (newFlowId === this.attachedFlowId()) return;

    this.updatingFlow.set(true);
    this.updateFlowError.set(null);

    this.contractService.updateContractFlow(this.submitTarget.id, newFlowId, this.submitTarget).subscribe({
      next: (r) => {
        // đồng bộ lại UI
        this.attachedFlowId.set(newFlowId);
        this.flowService.getFlowById(newFlowId).subscribe({
          next: rr => this.attachedFlow.set(rr?.data ?? null),
          error: () => this.attachedFlow.set(null)
        });
        this.updatingFlow.set(false);
        alert('Đã cập nhật luồng cho hợp đồng.');
      },
      error: () => {
        this.updatingFlow.set(false);
        this.updateFlowError.set('Cập nhật luồng thất bại.');
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

    // Nếu đang PENDING thì chặn
    if ((this.submitTarget.status || '').toUpperCase() === 'PENDING_APPROVAL' || this.currentFlow()?.exists) {
      this.submitError.set('Hợp đồng đã trong quy trình phê duyệt.');
      return;
    }

    const sel = this.selectedFlowId() ?? this.defaultFlow()?.id ?? null;
    if (!sel) {
      this.submitError.set('Chưa có luồng trình ký. Vui lòng chọn luồng.');
      return;
    }

    this.submitting.set(true);
    this.submitError.set(null);

    const needUpdate = sel !== this.attachedFlowId();

    const afterUpdate = () =>
      this.approvalService.submitForApproval(this.submitTarget!.id, sel).subscribe({
        next: (res) => {
          const updated = { ...res.data, filePath: (res.data as any)?.filePath ?? undefined } as ContractResponse;
          this.contracts.update(list => list.map(x => x.id === updated.id ? { ...x, ...updated } : x));
          this.submitting.set(false);
          this.showSubmit.set(false);
          alert('Đã trình ký thành công!');
        },
        error: (err) => {
          this.submitting.set(false);
          if (err?.status === 409) {
            this.submitError.set('Hợp đồng đã có luồng trình ký.');
          } else {
            this.submitError.set('Trình ký thất bại. Vui lòng thử lại.');
          }
        }
      });

    if (needUpdate) {
       this.contractService.updateContractFlow(this.submitTarget!.id, sel, this.submitTarget!).subscribe({
        next: () => { this.attachedFlowId.set(sel); afterUpdate(); },
        error: () => { this.submitting.set(false); this.submitError.set('Không cập nhật được luồng trước khi trình ký.'); }
      });
    } else {
      afterUpdate();
    }
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
    if (s === 'PENDING_APPROVAL') return 'Đang trình ký';
    if (s === 'APPROVED') return 'Đã ký';
    if (s === 'REJECTED' ) return 'Từ chối';
    if (s === 'CANCELLED') return 'Đã Huỷ';
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

    // Variable data for editing
  editVariables: { varName: string, varValue: string }[] = [];

  editForm: FormGroup = this.fb.group({
    title: [''],
    status: ['DRAFT']
  });
  showEdit = signal<boolean>(false);
  editContract: ContractResponse | null = null;

  // Open Edit Modal
  edit(item: ContractResponse): void {
    this.editContract = item;
    this.editForm.setValue({ title: item.title, status: item.status });

    // ƯU TIÊN variableValues, fallback sang variables (BE đang trả 'variables')
    const source = (item.variableValues && item.variableValues.length)
      ? item.variableValues
      : (item as any).variables ?? [];

    console.log('vars for edit:', source);          // <-- phải thấy mảng 8 phần tử như JSON
    this.editVariables = source.map((v: VariableValueResponse) => ({
      varName: v.varName,
      varValue: v.varValue ?? ''
    }));

    console.log('item:', item);
console.log('item.variables:', (item as any).variables);
console.log('item.variableValues:', item.variableValues);
console.log('editVariables:', this.editVariables);

    this.showEdit.set(true);
  }


  // Close Edit Modal
  closeEditModal(): void {
    this.showEdit.set(false);
    this.editContract = null;
  }

  // Submit the form to update contract
  submitEdit(): void {
    if (this.editContract && this.editForm.valid) {
      const updatedContract = {
        ...this.editContract,
        ...this.editForm.value,
        variables: this.editVariables
      };

      this.contractService.updateContract(updatedContract.id, updatedContract).subscribe({
        next: (response) => {
          // Update the contract in the list after successful update
          const updatedContracts = this.contracts().map(c =>
            c.id === updatedContract.id ? response.data : c
          );
          this.contracts.set(updatedContracts);
          this.closeEditModal();
        },
        error: (err) => {
          this.error.set('Cập nhật hợp đồng thất bại');
          console.error(err);
        }
      });
    }
  }

  openCancelModal(contract: ContractResponse): void {
    this.cancelContract = contract;
    this.showCancelModal.set(true);
  }

  // Đóng modal hủy hợp đồng
  closeCancelModal(): void {
    this.showCancelModal.set(false);
    this.cancelContract = null;
  }

  // Xác nhận hủy hợp đồng
  cancelContractAction(): void {
    if (this.cancelContract) {
      this.contractService.cancelContract(this.cancelContract.id).subscribe({
        next: () => {
          alert('Hợp đồng đã được hủy');
          this.fetchContracts(); // Cập nhật lại danh sách hợp đồng
          this.closeCancelModal(); // Đóng modal
        },
        error: (err) => {
          console.error(err);
          alert('Có lỗi xảy ra khi hủy hợp đồng');
        }
      });
    }
  }

  // Mở modal xóa hợp đồng
  openDeleteModal(contract: ContractResponse): void {
    this.deleteContract = contract;
    this.showDeleteModal.set(true);
  }

  // Đóng modal xóa hợp đồng
  closeDeleteModal(): void {
    this.showDeleteModal.set(false);
    this.deleteContract = null;
  }

  // Xác nhận xóa hợp đồng
  deleteContractAction(): void {
    if (this.deleteContract) {
      this.contractService.deleteContract(this.deleteContract.id).subscribe({
        next: () => {
          alert('Hợp đồng đã được xóa');
          this.fetchContracts(); // Cập nhật lại danh sách hợp đồng
          this.closeDeleteModal(); // Đóng modal
        },
        error: (err) => {
          console.error(err);
          alert('Có lỗi xảy ra khi xóa hợp đồng');
        }
      });
    }
  }


  flowsWithoutDefault(): ApprovalFlowResponse[] {
    const df = this.defaultFlow();
    return df ? (this.flows().filter(f => f.id !== df.id)) : this.flows();
  }

  // --- flow đang chọn (obj) ---
  selectedFlowObj(): ApprovalFlowResponse | null {
    const fid = this.selectedFlowId();
    if (!fid) return null;
    const df = this.defaultFlow();
    if (df && df.id === fid) return df;
    return this.flows().find(f => f.id === fid) || null;
  }

  private loadDefaultFlowThenFlows(templateId: number) {
    this.defaultFlow.set(null);
    this.flows.set([]);
    this.defaultFlowLoading.set(true);
    this.flowsLoading.set(true);

    // 1) default
    this.flowService.getDefaultFlowByTemplate(templateId).subscribe({
      next: (r) => {
        const df = r?.data ?? null;
        if (df) {
          df.steps = this.sortSteps(df.steps);
          this.defaultFlow.set(df);
          // chỉ preselect nếu chưa có attachedFlowId
          if (!this.attachedFlowId()) {
            this.selectedFlowId.set(df.id);
          }
        }
        this.defaultFlowLoading.set(false);
      },
      error: () => { this.defaultFlowLoading.set(false); this.defaultFlow.set(null); }
    });

    // 2) all flows
    this.flowService.listFlowsByTemplate(templateId).subscribe({
      next: (r) => {
        const list = (r?.data ?? []).map(f => ({ ...f, steps: this.sortSteps(f.steps) }));
        this.flows.set(list);
        // nếu chưa có attached & chưa có selected → chọn item đầu
        if (!this.attachedFlowId() && !this.selectedFlowId() && list.length > 0) {
          this.selectedFlowId.set(list[0].id);
        }
        this.flowsLoading.set(false);
      },
      error: () => { this.flowsLoading.set(false); }
    });
  }

  private loadAttachedFlow(flowId: number) {
    this.attachedFlowId.set(flowId);
    this.flowService.getFlowById(flowId).subscribe({
      next: (r) => {
        const f = r?.data ?? null;
        if (f) {
          f.steps = this.sortSteps(f.steps);
          this.attachedFlow.set(f);
          // ưu tiên hiển thị flow đang gắn
          this.selectedFlowId.set(flowId);
        }
      },
      error: () => { this.attachedFlow.set(null); }
    });
  }

  private sortSteps<T extends { stepOrder?: number | null }>(steps: T[] | null | undefined): T[] {
    return [...(steps ?? [])].sort((a, b) => {
      const aOrder = (a?.stepOrder ?? 0);
      const bOrder = (b?.stepOrder ?? 0);
      return aOrder - bOrder;
    });
  }
}