import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map, switchMap, catchError, finalize, tap, delay } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { ContractTemplateResponse, TemplateVariable } from '../../core/models/contract-template-response.model';
import { ApprovalFlowService, ApprovalFlowRequest, ApprovalStepRequest, 
  ApproverType,ApprovalAction, ApprovalFlowResponse } from '../../core/services/contract-flow.service';

import { DepartmentService, DepartmentResponse } from '../../core/services/department.service';
import { PositionService, PositionResponse } from '../../core/services/position.service';
import { EmployeeService, AuthProfileResponse } from '../../core/services/employee.service';
import {ContractService} from '../../core/services/contract.service';
import { CreateContractRequest,VariableValueRequest } from '../../core/models/contract.model';
import { ContractResponse } from '../../core/models/contract.model';
import { ContractApprovalService } from '../../core/services/contract-approval.service';


enum NewApproverType {
  USER = 'USER',
  POSITION = 'POSITION'
}
type ApproverTypeUI = 'USER' | 'POSITION';
type ApprovalActionUI = 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';

@Component({
  selector: 'app-contract-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './contract.component.html',
  styleUrl: './contract.component.scss'
})
export class ContractComponent implements OnInit {

  readonly requiredValidator = Validators.required;

  currentStep = 1;
  isSaving = false;
  successMessage = '';
  errorMessage = '';
  previewHtml = '';             
  lastCreatedFlowId: number|null = null; 

  pageSizeOptions = [4, 8, 12];
  pageSize = 4;
  currentPage = 1;


  selectedTemplate: ContractTemplateResponse | null = null;
  contractForm: FormGroup;
  templates: ContractTemplateResponse[] = [];

  departments: DepartmentResponse[] = [];
  positions: PositionResponse[] = [];
  employees: AuthProfileResponse[] = [];
  public readonly Math = Math;

  defaultFlow: ApprovalFlowResponse | null = null;
  defaultFlowLoading = false;
  defaultFlowError = '';

  positionsBySigner = new Map<number, PositionResponse[]>();


  constructor(
    private fb: FormBuilder,
    private contractTemplateService: ContractTemplateService,
    private approvalFlowService: ApprovalFlowService,
    private contractService: ContractService ,
    private departmentService: DepartmentService,
    private positionService: PositionService,
    private employeeService: EmployeeService,
    private toastr: ToastrService,
    private contractApprovalService: ContractApprovalService,
  ) {
    this.contractForm = this.fb.group({
      contractName: ['', Validators.required],
      contractNumber: [{ value: 'Tự động sinh', disabled: true }],
      contractDescription: [''],

      variables: this.fb.array([]),

      signatureConfig: this.fb.group({
        flowOption: ['', Validators.required], // 'default' | 'new' | 'existing'
        deadline: [''],
        flowName: [''],
        flowDescription: [''],
        newSigners: this.fb.array([])
      })
    });
  }

  ngOnInit(): void {
    this.loadTemplates();

    // 👇 NEW: tải dữ liệu tham chiếu
    this.loadReferences();

    const sigCfg = this.contractForm.get('signatureConfig')!;
    sigCfg.get('flowOption')!.valueChanges.subscribe((opt: string | null) => {
      const flowNameCtrl = sigCfg.get('flowName')!;
      if (opt === 'new') {
        flowNameCtrl.setValidators([Validators.required, Validators.minLength(3)]);
      } else {
        flowNameCtrl.clearValidators();
      }
      flowNameCtrl.updateValueAndValidity();

      if (opt === 'default') this.loadDefaultFlowForSelectedTemplate();
    });
  }

  // Getters
  get variablesFormArray(): FormArray {
    return this.contractForm.get('variables') as FormArray;
  }
  get newSignersFormArray(): FormArray {
    return this.contractForm.get('signatureConfig.newSigners') as FormArray;
  }

  // ====== Load dữ liệu danh mục từ service thật ======
  private loadReferences(): void {
    // Departments
    this.departmentService.getAllDepartments().subscribe({
      next: res => { this.departments = res?.data ?? []; },
      error: err => {
        console.error('Lỗi load departments:', err);
        this.departments = [];
      }
    });

    // Positions
    this.positionService.getAllPositions().subscribe({
      next: res => { this.positions = res?.data ?? []; },
      error: err => {
        console.error('Lỗi load positions:', err);
        this.positions = [];
      }
    });

    // Employees
    this.employeeService.getAll().subscribe({
      next: res => { this.employees = res?.data ?? []; },
      error: err => {
        console.error('Lỗi load employees:', err);
        this.employees = [];
      }
    });
  }

  // Load Templates
loadTemplates(): void {
  this.contractTemplateService.getAllTemplates().subscribe({
    next: (data) => {
      this.templates = data;
      this.currentPage = 1;
      if (this.templates.length > 0) {
        this.selectTemplate(this.templates[0]);
      }
    },
    error: (err) => {
      console.error('Lỗi khi tải templates:', err);
      this.errorMessage = 'Không thể tải danh sách template.';
    }
  });
}

  loadDefaultFlowForTemplate(templateId: number): void {
    this.contractTemplateService.getDefaultFlowByTemplate(templateId).subscribe({
      next: (flow: ApprovalFlowResponse) => {
        this.defaultFlow = flow;
      },
      error: (err) => {
        console.error('Lỗi khi tải luồng ký mặc định:', err);
        this.toastr.error('Không thể tải luồng ký mặc định cho template này.');
      }
    });
  }

  // Step 1
  selectTemplate(template: ContractTemplateResponse): void {
    this.selectedTemplate = template;
    this.loadVariablesForm(template);
    this.contractForm.get('signatureConfig.flowOption')?.setValue('default');
    this.defaultFlow = null;
    this.loadDefaultFlowForSelectedTemplate(); 
  }

  // Step 2
  loadVariablesForm(template: ContractTemplateResponse): void {
    this.variablesFormArray.clear();
    template.variables?.forEach(v => {
      this.variablesFormArray.push(this.fb.group({
        name: [v.varName],
        label: [v.varName],
        type: [(v as any).type ?? (v as any).dataType ?? 'TEXT'],
        value: ['', v.required ? Validators.required : null]
      }));
    });
  }

  // Step 3
  addSigner(): void {
    this.newSignersFormArray.push(this.buildNewSignerGroup());
  }
  removeSigner(index: number): void {
    this.newSignersFormArray.removeAt(index);
  }

  buildNewSignerGroup(): FormGroup {
    const g = this.fb.group({
      approverType: ['USER', Validators.required],   // USER | POSITION
      employeeId: [null],                             // required khi USER
      positionId: [null],                             // required khi POSITION
      departmentId: [null],                           // required khi POSITION
      required: [true, Validators.required],
      isFinalStep: [false, Validators.required],
      action: ['APPROVE_ONLY', Validators.required],  // APPROVE_ONLY | SIGN_ONLY | SIGN_THEN_APPROVE
      signaturePlaceholder: ['']
    });

    // 1) Switch validators theo approverType
      g.get('approverType')!.valueChanges.subscribe((val: string | null) => {
        const type = (val as 'USER' | 'POSITION') ?? 'USER';
        const emp = g.get('employeeId')!;
        const pos = g.get('positionId')!;
        const dep = g.get('departmentId')!;

      if (type === 'USER') {
        emp.setValidators(Validators.required);
        pos.clearValidators(); pos.setValue(null, { emitEvent: false });
        dep.clearValidators(); dep.setValue(null, { emitEvent: false });
      } else {
        emp.clearValidators(); emp.setValue(null, { emitEvent: false });
        dep.setValidators(Validators.required);
        pos.setValidators(Validators.required);

        // nếu có department sẵn thì nạp vị trí
        const idx = this.newSignersFormArray.controls.indexOf(g);
        const deptId = Number(dep.value);
        if (idx > -1 && deptId) this.loadPositionsForSigner(idx, deptId, Number(pos.value));
      }

      emp.updateValueAndValidity();
      pos.updateValueAndValidity();
      dep.updateValueAndValidity();
    });

    // 2) Khi đổi department → reset position + nạp danh sách vị trí
    g.get('departmentId')!.valueChanges.subscribe(val => {
      const idx = this.newSignersFormArray.controls.indexOf(g);
      g.get('positionId')!.setValue(null, { emitEvent: false });
      const deptId = Number(val);
      if (idx > -1) this.loadPositionsForSigner(idx, deptId);
    });

    // 3) Ràng buộc placeholder theo action
    const actionCtrl = g.get('action')!;
    const phCtrl = g.get('signaturePlaceholder')!;
    const syncPH = () => {
      const act = actionCtrl.value as string;
      if (act === 'APPROVE_ONLY') {
        phCtrl.clearValidators();
      } else {
        phCtrl.setValidators([Validators.required, Validators.minLength(3)]);
      }
      phCtrl.updateValueAndValidity({ emitEvent: false });
    };
    syncPH();
    actionCtrl.valueChanges.subscribe(syncPH);

    // 4) KÍCH HOẠT LẦN ĐẦU để apply validators đúng ngay khi thêm bước
    g.get('approverType')!.setValue(g.get('approverType')!.value, { emitEvent: true });

    return g;
  }

  // Step logic
  goToStep(step: number): void {
    if (!this.validateStep(this.currentStep)) return;
    this.currentStep = step;

    if (step === 4) {
      this.previewTemplate();
    }
  }

  previewTemplate(): void {
    if (!this.selectedTemplate) {
      this.errorMessage = 'Chưa chọn template để xem trước.';
      return;
    }
    const payload = this.buildPreviewRequest();
    this.errorMessage = '';
    this.previewHtml = '';

    this.contractService.previewTemplate(payload).subscribe({
      next: (res) => {
        this.previewHtml = res?.data ?? '';
      },
      error: (err) => {
        console.error('Lỗi preview template:', err);
        this.errorMessage = 'Không thể xem trước hợp đồng.';
      }
    });
  }


  validateStep(step: number): boolean {
    if (step === 1 && !this.selectedTemplate) {
      this.errorMessage = 'Vui lòng chọn một template để tiếp tục.';
      this.toastr.error(this.errorMessage);
      return false;
    }

    if (step === 2 && this.contractForm.get('variables')?.invalid) {
      this.errorMessage = 'Vui lòng điền đầy đủ thông tin bắt buộc trong hợp đồng.';
      this.toastr.error(this.errorMessage);
      this.contractForm.get('variables')?.markAllAsTouched();
      return false;
    }

    if (step === 3) {
      const sigCfg = this.contractForm.get('signatureConfig')!;
      if (!sigCfg.get('flowOption')?.value) {
        this.errorMessage = 'Vui lòng chọn một luồng ký để tiếp tục.';
        this.toastr.error(this.errorMessage);
        return false;
      }
      if (sigCfg.get('flowOption')?.value === 'new') {
        if (this.newSignersFormArray.length === 0 || this.newSignersFormArray.invalid) {
          this.errorMessage = 'Vui lòng thêm người ký hợp lệ cho flow mới.';
          this.toastr.error(this.errorMessage);
          this.newSignersFormArray.markAllAsTouched();
          return false;
        }
        if (sigCfg.get('flowName')?.invalid) {
          this.errorMessage = 'Tên flow bắt buộc (tối thiểu 3 ký tự).';
          this.toastr.error(this.errorMessage);
          sigCfg.get('flowName')?.markAsTouched();
          return false;
        }
      }
    }

    this.errorMessage = '';
    return true;
  }

  // Step 4 - Tạo hợp đồng
  createContract(submitNow: boolean): void {
  if (!this.validateStep(4)) return;

  this.isSaving = true;
  this.errorMessage = '';
  this.successMessage = '';

  const sigCfg = this.contractForm.get('signatureConfig')!.value;
  let createFlow$: Observable<unknown> = of(null);
  this.lastCreatedFlowId = null;

  // Kiểm tra nếu chọn 'new' tạo flow mới
  if (sigCfg.flowOption === 'new') {
    const steps: ApprovalStepRequest[] = this.newSignersFormArray.controls.map((ctrl, idx) => {
      const v = ctrl.value;
      const approverType: ApproverType = (v.approverType === 'POSITION') ? ApproverType.POSITION : ApproverType.USER;
      const action: ApprovalAction =
        (v.action === 'SIGN_ONLY') ? ApprovalAction.SIGN_ONLY
        : (v.action === 'SIGN_THEN_APPROVE') ? ApprovalAction.SIGN_THEN_APPROVE
        : ApprovalAction.APPROVE_ONLY;

      const needsSign = action !== ApprovalAction.APPROVE_ONLY;

      return {
        stepOrder: idx + 1,
        required: !!v.required,
        isFinalStep: !!v.isFinalStep,
        approverType,
        action,
        employeeId: approverType === ApproverType.USER ? v.employeeId : undefined,
        positionId: approverType === ApproverType.POSITION ? v.positionId : undefined,
        departmentId: approverType === ApproverType.POSITION ? v.departmentId : undefined,
        signaturePlaceholder: needsSign ? (v.signaturePlaceholder || 'SIGN') : undefined
      };
    });

    const flowReq: ApprovalFlowRequest = {
      name: (sigCfg.flowName || `Flow phê duyệt - ${this.contractForm.get('contractName')?.value || 'Không tên'}`).trim(),
      description: sigCfg.flowDescription || '',
      templateId: this.selectedTemplate?.id as number, // gắn template đang chọn
      steps
    };

    createFlow$ = this.approvalFlowService.createFlow(flowReq).pipe(
      tap(res => {
        const id = res?.data?.id;
        this.lastCreatedFlowId = typeof id === 'number' ? id : null;
      })
    );
  }

  // Kiểm tra flowOption là 'default', lấy flow từ template
  if (sigCfg.flowOption === 'default' && this.selectedTemplate?.defaultFlowId) {
    this.loadDefaultFlowForTemplate(this.selectedTemplate.defaultFlowId);  
  }

  // Tạo hợp đồng → (nếu submitNow) gọi trình ký ngay
  createFlow$
    .pipe(
      switchMap(() => {
        const req = this.buildCreateRequest(this.lastCreatedFlowId); // Gửi yêu cầu tạo hợp đồng
        return this.contractService.createContract(req); // Gửi hợp đồng
      }),
      switchMap((res) => {
        const contractId = res?.data?.id as number | undefined;
        if (!submitNow || !contractId) return of(res);

        // Xác định flowId để submit: 'new' dùng flow vừa tạo, 'default' / 'existing' → null để BE tự chọn
        const flowIdForSubmit =
          sigCfg.flowOption === 'new' ? (this.lastCreatedFlowId ?? null) : null;

        return this.contractApprovalService
          .submitForApproval(contractId, flowIdForSubmit ?? undefined) // Gọi submit nếu cần
          .pipe(map(() => res));
      }),
      finalize(() => (this.isSaving = false)) // Kết thúc
    )
    .subscribe({
      next: () => {
        if (submitNow) {
          this.successMessage = '🎉 Đã tạo hợp đồng và trình ký thành công!';
        } else {
          this.successMessage = '💾 Đã lưu nháp hợp đồng thành công!';
        }
        this.toastr.success(this.successMessage);
        this.currentStep = 1;
        this.selectedTemplate && this.loadVariablesForm(this.selectedTemplate);
      },
      error: (err) => {
        console.error('Create/Submit error:', err);
        this.errorMessage = submitNow
          ? 'Không thể tạo hoặc trình ký hợp đồng. Vui lòng thử lại.'
          : 'Không thể lưu nháp hợp đồng. Vui lòng thử lại.';
        this.toastr.error(this.errorMessage);
      }
    }); 
}



  // contract.component.ts (helper ngắn)
  getCategoryLabelFromTemplate(t: ContractTemplateResponse): string {
    if (t.categoryName) return t.categoryName;
    const code = (t.categoryCode || '').toUpperCase();
    const byCode: Record<string,string> = {
      LABOR: 'Hợp đồng lao động',
      BUSINESS: 'Hợp đồng kinh doanh',
      SERVICE: 'Hợp đồng dịch vụ',
      RENTAL: 'Hợp đồng thuê',
      LEGAL: 'Pháp lý',
    };
    if (byCode[code]) return byCode[code];

    const byId: Record<number,string> = {
      1: 'Hợp đồng lao động',
      2: 'Hợp đồng kinh doanh',
      3: 'Hợp đồng dịch vụ',
      4: 'Hợp đồng thuê',
      5: 'Pháp lý',
    };
    return t.categoryId ? (byId[t.categoryId] || 'Khác') : 'Khác';
  }


  goToPage(p: number): void {
    if (p < 1 || p > this.totalPages) return;
    this.currentPage = p;
  }
  prevPage(): void {
    this.goToPage(this.currentPage - 1);
  }
  nextPage(): void {
    this.goToPage(this.currentPage + 1);
  }
  onChangePageSize(size: number | string): void {
    const n = Number(size) || this.pageSize;
    this.pageSize = n;
    this.currentPage = 1; // reset về trang 1 khi đổi page size
  }



  // Helpers
  getInputType(variableType: string): string {
    const types: any = { 'TEXT': 'text', 'NUMBER': 'number', 'DATE': 'date' };
    return types[variableType] || 'text';
  }
  getCategoryLabel(category: string): string {
    const labels: any = { 'labor': 'Lao động', 'sale': 'Mua bán', 'rental': 'Thuê nhà', 'service': 'Dịch vụ' };
    return labels[category] || category;
  }
  getTypeLabel(type: string): string {
    const labels: any = { 'TEXT': 'Text', 'NUMBER': 'Số', 'DATE': 'Ngày' };
    return labels[type] || type;
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
  get pagedTemplates() {
    const start = (this.currentPage - 1) * this.pageSize;
    return this.templates.slice(start, start + this.pageSize);
  }

  /** Map FormArray -> VariableValueRequest[] cho BE */
    private buildVariablePayload(): VariableValueRequest[] {
      return this.variablesFormArray.controls.map(ctrl => {
        const v = ctrl.value;
        return {
          varName: v.name,
          varValue: v.value ?? ''
        } as VariableValueRequest;
      });
    }

    /** Tạo payload Preview (không bắt buộc flowId) */
    private buildPreviewRequest(): CreateContractRequest {
      return {
        templateId: this.selectedTemplate?.id as number,
        title: this.contractForm.get('contractName')?.value ?? '',
        variables: this.buildVariablePayload(),
        // Không cần flowId cho preview
        allowChangeFlow: true
      };
    }

    /** Tạo payload Create (có thể kèm flowId nếu có) */
    private buildCreateRequest(flowId?: number|null): CreateContractRequest {
      return {
        templateId: this.selectedTemplate?.id as number,
        title: this.contractForm.get('contractName')?.value ?? '',
        variables: this.buildVariablePayload(),
        flowId: flowId ?? null,
        allowChangeFlow: true
      };
    }
// load flow default
    private loadDefaultFlowForSelectedTemplate(): void {
      this.defaultFlow = null;
      this.defaultFlowError = '';
      if (!this.selectedTemplate?.id) return;

      this.defaultFlowLoading = true;
      this.approvalFlowService.getDefaultFlowByTemplate(this.selectedTemplate.id)
        .pipe(finalize(() => (this.defaultFlowLoading = false)))
        .subscribe({
          next: (res) => {
            this.defaultFlow = res?.data ?? null;
            // chuẩn hóa thứ tự bước (nếu có)
            if (this.defaultFlow?.steps?.length) {
              this.defaultFlow.steps = [...this.defaultFlow.steps].sort(
                (a, b) => (a.stepOrder ?? 0) - (b.stepOrder ?? 0)
              );
            }
          },
          error: (err) => {
            console.error('Lỗi load default flow:', err);
            this.defaultFlow = null;
            this.defaultFlowError = 'Không tìm thấy luồng ký mặc định cho template này.';
          }
        });
    }

    private loadPositionsForSigner(signerIndex: number, deptId: number, keepPositionId?: number | null) {
      if (!deptId) {
        this.positionsBySigner.set(signerIndex, []);
        this.newSignersFormArray.at(signerIndex)?.get('positionId')?.setValue(null, { emitEvent: false });
        return;
      }

      this.positionService.getPositionsByDepartment(deptId).subscribe({
        next: res => {
          const list = res?.data ?? [];
          this.positionsBySigner.set(signerIndex, list);

          if (keepPositionId != null) {
            const exists = list.some(p => Number(p.id) === Number(keepPositionId));
            if (!exists) {
              this.newSignersFormArray.at(signerIndex)?.get('positionId')?.setValue(null, { emitEvent: false });
            }
          }
        },
        error: _ => {
          this.positionsBySigner.set(signerIndex, []);
          this.newSignersFormArray.at(signerIndex)?.get('positionId')?.setValue(null, { emitEvent: false });
        }
      });
    }

  trackByTemplateId = (_: number, t: ContractTemplateResponse) => t?.id;
  trackByIndex = (index: number) => index;
  trackByStepOrder = (_: number, s: { stepOrder?: number }) => s?.stepOrder ?? _;
}
