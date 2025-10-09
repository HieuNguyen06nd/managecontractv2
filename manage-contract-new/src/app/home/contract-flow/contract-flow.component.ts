import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { FormsModule } from '@angular/forms';
import { ContractTemplateResponse } from '../../core/models/contract-template-response.model';
import {
  ContractTemplateService
} from '../../core/services/contract-template.service';

import {
  ApprovalFlowService,
  ApprovalFlowRequest,
  ApprovalStepRequest,
  ApprovalFlowResponse,
  ApprovalStepResponse,
  ApproverType,
  ApprovalAction
} from '../../core/services/contract-flow.service';

import {
  DepartmentService,
  DepartmentResponse
} from '../../core/services/department.service';

import {
  PositionService,
  PositionResponse
} from '../../core/services/position.service';

import {
  EmployeeService,
} from '../../core/services/employee.service';
import { AuthProfileResponse } from '../../core/models/auth.model';

type ApproverTypeUI = 'USER' | 'POSITION';
type ApprovalActionUI = 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';

@Component({
  selector: 'app-contract-flow',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './contract-flow.component.html',
  styleUrls: ['./contract-flow.component.scss']
})
export class ContractFlowComponent implements OnInit {

  // ====== VIEW MODE ======
  viewMode: 'list' | 'create' = 'list';

  // ====== STEPPER ======
  currentStep = 1;

  // ====== DROPDOWNS ======
  departments: DepartmentResponse[] = [];
  positions: PositionResponse[] = [];
  employees: AuthProfileResponse[] = [];

  // ====== TEMPLATES ======
  templates: ContractTemplateResponse[] = [];
  templateSearch = '';
  templateCategoryFilter = '';
  compareById = (a: any, b: any) => a != null && b != null && String(a) === String(b);

  // ====== LIST MODE ======
  selectedTemplateForList: number | null = null;
  flows: ApprovalFlowResponse[] = [];

  /** FlowId đang mở rộng */
  expanded = new Set<number>();
  /** FlowId đang loading step */
  isLoadingDetail = new Set<number>();
  /** Cache steps theo flowId (để không gọi API nhiều lần) */
  private stepCache = new Map<number, ApprovalStepResponse[]>();
  editingFlowId: number | null = null;

  // ====== FORM ======
  form!: FormGroup;
  positionsByStep = new Map<number, PositionResponse[]>();

  isDeleteModalOpen = false;
  deleteFlowId: number | null = null;
  deleteFlowName = '';
    
  constructor(
    private fb: FormBuilder,
    private toastr: ToastrService,
    private templateService: ContractTemplateService,
    private approvalFlowService: ApprovalFlowService,
    private departmentService: DepartmentService,
    private positionService: PositionService,
    private employeeService: EmployeeService
  ) {
    this.form = this.fb.group({
      // Step 1
      flowName: ['', [Validators.required, Validators.minLength(3)]],
      flowDescription: [''],
      allowCustomFlow: [true],

      // Step 2
      steps: this.fb.array([]),

      // Step 3
      selectedTemplateId: [null, Validators.required],
      allowOverrideFlow: [true]
    });
  }

  // ================= LIFECYCLE =================

  ngOnInit(): void {
    this.initEmptyForm();
    this.loadReferences();
    this.loadTemplates();
  }

  // ================= VIEW SWITCH =================

  switchToList() {
    this.viewMode = 'list';
    if (this.selectedTemplateForList) this.loadFlowsForList();
  }

  switchToCreate(reset = false) {
    this.viewMode = 'create';
    if (reset) this.resetUiStateForCreate();
  }

  // ================= LIST MODE =================

  loadFlowsForList(): void {
    if (!this.selectedTemplateForList) {
      this.flows = [];
      return;
    }
    this.approvalFlowService.listFlowsByTemplate(this.selectedTemplateForList).subscribe({
      next: (res) => {
        // Sắp xếp luồng, đưa luồng mặc định lên đầu
        this.flows = res?.data ?? [];
        this.flows.sort((a, b) => (b.isDefault ? 1 : 0) - (a.isDefault ? 1 : 0));  // Đảm bảo luồng mặc định lên đầu

        this.expanded.clear();
        this.isLoadingDetail.clear();
        this.stepCache.clear();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể tải danh sách flow');
        this.flows = [];
      }
    });
  }


  toggleSteps(flowId: number) {
    if (this.expanded.has(flowId)) {
      this.expanded.delete(flowId);
      return;
    }
    this.expanded.add(flowId);

    if (this.stepCache.has(flowId)) return;

    this.isLoadingDetail.add(flowId);
    this.approvalFlowService.getFlowById(flowId).subscribe({
      next: (res) => {
        const flow = res?.data;
        const steps = (flow?.steps || [])
          .slice()
          .sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0));
        this.stepCache.set(flowId, steps);
        this.isLoadingDetail.delete(flowId);
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không tải được bước của flow');
        this.isLoadingDetail.delete(flowId);
      }
    });
  }

  stepsFor(f: ApprovalFlowResponse): ApprovalStepResponse[] | undefined {
    const cached = this.stepCache.get(f.id);
    if (cached) return cached;
    if (f.steps && f.steps.length) {
      return [...f.steps].sort((a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0));
    }
    return undefined;
  }

  trackByFlowId(_i: number, f: ApprovalFlowResponse) { return f.id; }

  viewFlow(id: number) {
    this.approvalFlowService.getFlowById(id).subscribe({
      next: (res) => {
        console.log('Flow detail', res?.data);
        this.toastr.info('Xem chi tiết flow – bạn có thể mở modal tuỳ biến.');
      }
    });
  }

editFlow(id: number) {
  this.approvalFlowService.getFlowById(id).subscribe({
    next: (res) => {
      const flow = res?.data;
      if (!flow) {
        this.toastr.error('Không tìm thấy flow');
        return;
      }

      // chuyển sang màn tạo/sửa & về bước đầu
      this.viewMode = 'create';
      this.currentStep = 1;

      // đánh dấu đang sửa
      this.editingFlowId = flow.id ?? id;

      // đổ dữ liệu chung
      this.form.patchValue({
        flowName: flow.name ?? '',
        flowDescription: flow.description ?? '',
        selectedTemplateId: flow.templateId ?? null,
        allowCustomFlow: this.form.get('allowCustomFlow')?.value ?? true,
        allowOverrideFlow: this.form.get('allowOverrideFlow')?.value ?? true
      });

      // clear & đổ lại steps theo đúng thứ tự
      this.steps.clear();
      const sorted = (flow.steps || []).slice().sort((a,b)=>(a.stepOrder??0)-(b.stepOrder??0));
      sorted.forEach(s => {
        const grp = this.fb.group({
          approverType: <ApproverTypeUI>((s.approverType as any) ?? 'USER'),
          employeeId: [s.employeeId ?? null],
          positionId: [s.positionId ?? null],
          departmentId: [s.departmentId ?? null],

          // THÊM 2 DÒNG NÀY:
          action: <ApprovalActionUI>((s as any).action ?? 'APPROVE_ONLY'),
          signaturePlaceholder: [(s as any).signaturePlaceholder ?? ''],

          required: [!!s.required, Validators.required],
          isFinalStep: [!!s.isFinalStep, Validators.required]
        });

        this.steps.push(grp);
        this.onChangeApproverType(this.steps.length - 1);
        this.onChangeAction(this.steps.length - 1);
      });

      this.toastr.success('Đã nạp dữ liệu flow vào form để chỉnh sửa.');
    },
    error: (err) => {
      console.error(err);
      this.toastr.error('Không tải được chi tiết flow');
    }
  });
}


  setDefault(flowId: number): void {
    if (!this.selectedTemplateForList) return;

    this.approvalFlowService.setDefaultFlow(this.selectedTemplateForList, flowId).subscribe({
      next: () => {
        this.toastr.success('Đã đặt flow mặc định cho template');

        this.flows.forEach((flow) => {
          if (flow.id === flowId) {
            flow.isDefault = true;  
          } else {
            flow.isDefault = false;  
          }
        });

        this.flows.sort((a, b) => (b.isDefault ? 1 : 0) - (a.isDefault ? 1 : 0));
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể đặt mặc định');
      }
    });
  }


  deleteFlow(flowId: number) {
    if (!confirm('Bạn có chắc muốn xoá flow này?')) return;
    this.approvalFlowService.deleteFlow(flowId).subscribe({
      next: () => {
        this.toastr.success('Đã xoá flow');
        this.loadFlowsForList();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể xoá flow');
      }
    });
  }

  // ================= FORM GETTERS =================

  get steps(): FormArray {
    return this.form.get('steps') as FormArray;
  }

  // ================= LOAD DROPDOWN DATA =================

  private loadReferences(): void {
    this.departmentService.getAllDepartments().subscribe({
      next: res => (this.departments = res?.data ?? []),
      error: err => {
        console.error(err);
        this.departments = [];
      }
    });
    this.positionService.getAllPositions().subscribe({
      next: res => (this.positions = res?.data ?? []),
      error: err => {
        console.error(err);
        this.positions = [];
      }
    });
    this.employeeService.getAll().subscribe({
      next: res => {
        const list = res?.data ?? [];
        this.employees = list.map(u => ({
          ...u,
          id: typeof u.id === 'string' ? Number(u.id) : u.id
        }));
      },
      error: err => {
        console.error(err);
        this.employees = [];
      }
    });
  }

  private loadTemplates(): void {
    this.templateService.getAllTemplates().subscribe({
      next: data => (this.templates = data || []),
      error: err => {
        console.error(err);
        this.toastr.error('Không thể tải danh sách template');
      }
    });
  }

  // ================= FILTERED TEMPLATES (GETTER) =================

  get filteredTemplates(): ContractTemplateResponse[] {
    const q = (this.templateSearch || '').trim().toLowerCase();
    const cat = (this.templateCategoryFilter || '').toLowerCase();

    return (this.templates || []).filter(t => {
      const bySearch =
        !q ||
        (t.name || '').toLowerCase().includes(q) ||
        (t.description || '').toLowerCase().includes(q) ||
        (t.categoryName || '').toLowerCase().includes(q) ||
        (t.categoryCode || '').toLowerCase().includes(q);

      const byCat =
        !cat ||
        (t.categoryCode || '').toLowerCase() === cat ||
        (t.categoryName || '').toLowerCase().includes(cat);

      return bySearch && byCat;
    });
  }

  trackByTemplateId(_i: number, t: ContractTemplateResponse) {
    return t.id;
  }

  // ================= STEPPER ACTIONS =================

    goToStep(step: number): void {
      if (!this.validateStep(this.currentStep)) return;

      if (this.editingFlowId && step === 3) {
        this.currentStep = 4;
      } else {
        this.currentStep = step;
      }
    }


  private validateStep(step: number): boolean {
    if (step === 1) {
      const ok = this.form.get('flowName')?.valid;
      if (!ok) {
        this.form.get('flowName')?.markAsTouched();
        this.toastr.warning('Vui lòng nhập tên luồng phê duyệt (tối thiểu 3 ký tự).');
        return false;
      }
    }
    if (step === 2) {
      if (this.steps.length === 0 || this.steps.invalid) {
        this.steps.markAllAsTouched();
        this.toastr.warning('Vui lòng thêm ít nhất một bước phê duyệt hợp lệ.');
        return false;
      }
    }
    if (step === 3) {
      const ok = this.form.get('selectedTemplateId')?.valid;
      if (!ok) {
        this.form.get('selectedTemplateId')?.markAsTouched();
        this.toastr.warning('Vui lòng chọn một template.');
        return false;
      }
    }
    return true;
  }

  // ================= STEPS CRUD (STEP 2) =================

  addStep(): void {
    this.steps.push(
      this.fb.group({
        approverType: <ApproverTypeUI>'USER', // USER | POSITION

        // USER
        employeeId: [null, []],

        // POSITION
        positionId: [null, []],
        departmentId: [null, []],

        // Action + placeholder ký
        action: <ApprovalActionUI>'APPROVE_ONLY',
        signaturePlaceholder: [''],

        required: [true, Validators.required],
        isFinalStep: [false, Validators.required]
      })
    );
    const idx = this.steps.length - 1;
    this.onChangeApproverType(idx);
    this.onChangeAction(idx);
  }

  removeStep(i: number): void {
    this.steps.removeAt(i);
  }

  onChangeApproverType(index: number): void {
    const group = this.steps.at(index) as FormGroup;
    const type = group.get('approverType')?.value as ApproverTypeUI;

    const employeeId = group.get('employeeId');
    const positionId = group.get('positionId');
    const departmentId = group.get('departmentId');

    if (type === 'USER') {
      employeeId?.setValidators([Validators.required]);
      positionId?.clearValidators();
      departmentId?.clearValidators();
      positionId?.setValue(null, { emitEvent: false });
      departmentId?.setValue(null, { emitEvent: false });
    } else {
      positionId?.setValidators([Validators.required]);
      departmentId?.setValidators([Validators.required]);

      employeeId?.clearValidators();
      employeeId?.setValue(null, { emitEvent: false });
      const deptVal = Number(departmentId?.value);
      if (deptVal) this.loadPositionsForStep(index, deptVal, Number(positionId?.value));
    }
    employeeId?.updateValueAndValidity();
    positionId?.updateValueAndValidity();
    departmentId?.updateValueAndValidity();

    // Khi đổi approverType, action có thể đang là bước ký -> vẫn giữ validate placeholder
    this.onChangeAction(index);
  }

  onChangeAction(index: number): void {
    const group = this.steps.at(index) as FormGroup;
    const action = group.get('action')?.value as ApprovalActionUI;
    const placeholderCtrl = group.get('signaturePlaceholder')!;
    const requiresSign = action === 'SIGN_ONLY' || action === 'SIGN_THEN_APPROVE';
    if (requiresSign) {
      placeholderCtrl.setValidators([Validators.required, Validators.minLength(2)]);
    } else {
      placeholderCtrl.clearValidators();
      placeholderCtrl.setValue('', { emitEvent: false });
    }
    placeholderCtrl.updateValueAndValidity();
  }

  onChangeDepartment(index: number): void {
    const group = this.steps.at(index) as FormGroup;
    const deptId = Number(group.get('departmentId')?.value);

    // reset position mỗi lần đổi dept
    group.get('positionId')?.setValue(null, { emitEvent: false });

    if (!deptId) {
      this.positionsByStep.set(index, []);
      return;
    }
    this.loadPositionsForStep(index, deptId);
  }



  // ================= HELPERS =================

  hasSignature(e: any): boolean {
    return !!(e?.signatureImage || e?.signatureUrl || e?.signature);
  }

  stepRequiresSignature(i: number): boolean {
    const action = this.steps.at(i).get('action')?.value as ApprovalActionUI;
    return action === 'SIGN_ONLY' || action === 'SIGN_THEN_APPROVE';
  }

  actionLabel(a: ApprovalActionUI | null | undefined): string {
    switch (a) {
      case 'SIGN_ONLY': return 'Chỉ ký';
      case 'SIGN_THEN_APPROVE': return 'Ký rồi phê duyệt';
      default: return 'Chỉ phê duyệt';
    }
  }

  getCategoryLabel(t: ContractTemplateResponse): string {
    if (t.categoryName) return t.categoryName;
    const code = (t.categoryCode || '').toUpperCase();
    const map: Record<string, string> = {
      LABOR: 'Lao động',
      BUSINESS: 'Kinh doanh',
      SERVICE: 'Dịch vụ',
      RENTAL: 'Thuê',
      LEGAL: 'Pháp lý'
    };
    return map[code] || 'Khác';
  }

  getApproverBadge(type: ApproverTypeUI): string {
    return type === 'USER' ? 'Nhân viên' : 'Vị trí';
  }

  empNameById(id: unknown): string {
    const n = Number(id);
    const e = this.employees?.find(x => x.id === n);
    return e?.fullName || '—';
  }

  posNameById(id: unknown): string {
    const n = Number(id);
    const p = this.positions?.find(x => x.id === n);
    return p?.name || '—';
  }

  deptNameById(id: unknown): string {
    const n = Number(id);
    const d = this.departments?.find(x => x.id === n);
    return d?.name || '—';
  }

  templateNameById(id: unknown): string {
    const n = Number(id);
    const t = this.templates?.find(x => x.id === n);
    return t?.name || 'Chưa chọn';
  }

  // ================= SUBMIT (CREATE FLOW) =================

  createApprovalFlow(): void {
    if (!this.validateStep(4)) return;

    const steps: ApprovalStepRequest[] = this.steps.controls.map((ctrl, idx) => {
      const v = ctrl.value as {
        approverType: ApproverTypeUI;
        employeeId: number | null;
        positionId: number | null;
        departmentId: number | null;
        action: ApprovalActionUI;
        signaturePlaceholder: string;
        required: boolean;
        isFinalStep: boolean;
      };

      const type = v.approverType === 'USER' ? ApproverType.USER : ApproverType.POSITION;
      const needsSign = v.action === 'SIGN_ONLY' || v.action === 'SIGN_THEN_APPROVE';

      return {
        stepOrder: idx + 1,
        required: !!v.required,
        isFinalStep: !!v.isFinalStep,
        approverType: type,
        action: (v.action as any as ApprovalAction), 
        signaturePlaceholder: (v.action === 'SIGN_ONLY' || v.action === 'SIGN_THEN_APPROVE') ? (v.signaturePlaceholder || '').trim() : undefined,
        employeeId: type === ApproverType.USER ? v.employeeId ?? undefined : undefined,
        positionId: type === ApproverType.POSITION ? v.positionId ?? undefined : undefined,
        departmentId: type === ApproverType.POSITION ? v.departmentId ?? undefined : undefined
      };
    });

    const payload: ApprovalFlowRequest = {
      name: (this.form.get('flowName')?.value || '').trim(),
      description: this.form.get('flowDescription')?.value || '',
      templateId: this.form.get('selectedTemplateId')?.value,
      steps
    };

    if (this.editingFlowId) {
      // Cập nhật flow nếu đang chỉnh sửa
      this.approvalFlowService.updateFlow(this.editingFlowId, payload).subscribe({
        next: (res) => {
          this.toastr.success('Cập nhật Luồng Phê duyệt thành công!');
          this.switchToList();
          this.resetUiStateForCreate();
          this.loadFlowsForList();
        },
        error: (err) => {
          console.error(err);
          this.toastr.error('Không thể cập nhật Luồng Phê duyệt.');
        }
      });
    } else {
      // Tạo mới flow
      this.approvalFlowService.createFlow(payload).subscribe({
        next: (res) => {
          this.toastr.success('Tạo Luồng Phê duyệt thành công!');
          this.switchToList();
          this.resetUiStateForCreate();
          this.loadFlowsForList();
        },
        error: (err) => {
          console.error(err);
          this.toastr.error('Không thể tạo Luồng Phê duyệt.');
        }
      });
    }
  }

  openDeleteModal(flow: ApprovalFlowResponse): void {
    this.deleteFlowId = flow.id;
    this.deleteFlowName = flow.name ?? `#${flow.id}`;
    this.isDeleteModalOpen = true;
  }

  closeDeleteModal(): void {
    this.isDeleteModalOpen = false;
    this.deleteFlowId = null;
    this.deleteFlowName = '';
  }

  confirmDelete(): void {
    if (!this.deleteFlowId) return;
    this.approvalFlowService.deleteFlow(this.deleteFlowId).subscribe({
      next: () => {
        this.toastr.success('Đã xoá flow');
        this.closeDeleteModal();
        this.loadFlowsForList();
      },
      error: (err) => {
        console.error(err);
        this.toastr.error('Không thể xoá flow');
      }
    });
  }

  private initEmptyForm(): void {
    this.form = this.fb.group({
      flowName: ['', [Validators.required, Validators.minLength(3)]],
      flowDescription: [''],
      allowCustomFlow: [true],
      steps: this.fb.array([]),
      selectedTemplateId: [null, Validators.required],
      allowOverrideFlow: [true],
    });
  }

  /** Reset mọi state để bắt đầu tạo flow mới */
  private resetUiStateForCreate(): void {
    this.editingFlowId = null;
    this.currentStep = 1;
    this.initEmptyForm();
    this.form.markAsPristine();
    this.form.markAsUntouched();
  }

  private resetFormAfterCreate() {
    this.form.reset({
      flowName: '',
      flowDescription: '',
      allowCustomFlow: true,
      steps: [],
      selectedTemplateId: null,
      allowOverrideFlow: true
    });
    this.steps.clear();
    this.currentStep = 1;
  }

  private loadPositionsForStep(stepIndex: number, deptId: number, keepPositionId?: number | null) {
    if (!deptId) {
      this.positionsByStep.set(stepIndex, []);
      // clear position khi chưa chọn dept
      this.steps.at(stepIndex).get('positionId')?.setValue(null, { emitEvent: false });
      return;
    }

    this.positionService.getPositionsByDepartment(deptId).subscribe({
      next: res => {
        const list = res?.data ?? [];
        this.positionsByStep.set(stepIndex, list);

        // nếu đang sửa flow: giữ lại position cũ nếu còn trong danh sách
        if (keepPositionId != null) {
          const exists = list.some(p => Number(p.id) === Number(keepPositionId));
          if (!exists) {
            this.steps.at(stepIndex).get('positionId')?.setValue(null, { emitEvent: false });
          }
        }
      },
      error: err => {
        console.error(err);
        this.positionsByStep.set(stepIndex, []);
        this.steps.at(stepIndex).get('positionId')?.setValue(null, { emitEvent: false });
      }
    });
  }

}
