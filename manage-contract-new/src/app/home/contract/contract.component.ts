import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, FormArray, Validators, ReactiveFormsModule, AbstractControl } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map, switchMap, catchError, finalize, tap } from 'rxjs/operators';
import { ToastrService } from 'ngx-toastr';
import { ContractTemplateService, Status } from '../../core/services/contract-template.service';
import { ContractTemplateResponse, TemplateVariable  } from '../../core/models/contract-template-response.model';
import { VariableType } from '../../core/models/template-preview-response.model';
import { ApprovalFlowService, ApprovalFlowRequest, ApprovalStepRequest, 
  ApproverType,ApprovalAction, ApprovalFlowResponse } from '../../core/services/contract-flow.service';

import { DepartmentService, DepartmentResponse } from '../../core/services/department.service';
import { PositionService } from '../../core/services/position.service';
import { PositionResponse } from '../../core/models/position.model';
import { EmployeeService } from '../../core/services/employee.service';
import { AuthProfileResponse } from '../../core/models/auth.model';
import {ContractService} from '../../core/services/contract.service';
import { CreateContractRequest,VariableValueRequest } from '../../core/models/contract.model';
import { ContractResponse } from '../../core/models/contract.model';
import { ContractApprovalService } from '../../core/services/contract-approval.service';
import { SafeUrlPipe } from '../../shared/pipes/safe-url.pipe';

// Extended interface để hỗ trợ các thuộc tính mở rộng
interface ExtendedTemplateVariable extends TemplateVariable {
  name?: string;
  config?: any;
  allowedValues?: string[];
}

enum NewApproverType {
  USER = 'USER',
  POSITION = 'POSITION'
}
type ApproverTypeUI = 'USER' | 'POSITION';
type ApprovalActionUI = 'APPROVE_ONLY' | 'SIGN_ONLY' | 'SIGN_THEN_APPROVE';

@Component({
  selector: 'app-contract-create',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, SafeUrlPipe],
  templateUrl: './contract.component.html',
  styleUrl: './contract.component.scss'
})
export class ContractComponent implements OnInit {

  readonly requiredValidator = Validators.required;
  Status = Status;

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

  VariableType = VariableType;

  constructor(
    private fb: FormBuilder,
    private contractTemplateService: ContractTemplateService,
    private approvalFlowService: ApprovalFlowService,
    private contractService: ContractService,
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
        flowOption: ['', Validators.required],
        deadline: [''],
        flowName: [''],
        flowDescription: [''],
        newSigners: this.fb.array([])
      })
    });
  }

  ngOnInit(): void {
     this.loadTemplates(Status.ACTIVE); 
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
  loadTemplates(status: Status = Status.ACTIVE): void {
    this.contractTemplateService.getAllTemplatesByStatus(status).subscribe({
      next: (data) => {
        this.templates = data || [];
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

  // Step 2 - Load variables form với đầy đủ thông tin từ template
  loadVariablesForm(template: ContractTemplateResponse): void {
    this.variablesFormArray.clear();
    
    template.variables?.forEach((variable, index) => {
      const variableGroup = this.buildVariableFormGroup(variable);
      this.variablesFormArray.push(variableGroup);
    });
  }

  private buildVariableFormGroup(variable: TemplateVariable): FormGroup {
    // Cast variable để truy cập các thuộc tính mở rộng
    const extendedVar = variable as ExtendedTemplateVariable;
    
    let defaultValue: any = '';
    let validators = variable.required ? [Validators.required] : [];

    switch (variable.varType) {
      case VariableType.BOOLEAN:
        defaultValue = false;
        break;
      case VariableType.NUMBER:
        defaultValue = null;
        validators.push(Validators.pattern(/^-?\d*\.?\d+$/));
        if (extendedVar.config?.min !== undefined) {
          validators.push(Validators.min(extendedVar.config.min));
        }
        if (extendedVar.config?.max !== undefined) {
          validators.push(Validators.max(extendedVar.config.max));
        }
        break;
      case VariableType.DATE:
        defaultValue = '';
        break;
      case VariableType.DROPDOWN:
        defaultValue = extendedVar.config?.options?.[0] || '';
        break;
      case VariableType.LIST:
        defaultValue = extendedVar.config?.items?.[0] || '';
        break;
      case VariableType.TABLE:
         defaultValue = this.initializeTableData(extendedVar.config);
        break;
      case VariableType.TEXTAREA:
        defaultValue = '';
        break;
      default: // STRING và các kiểu khác
        defaultValue = '';
    }

    return this.fb.group({
      varName: [variable.varName],
      name: [extendedVar.name || variable.varName],
      varType: [variable.varType],
      required: [variable.required],
      config: [extendedVar.config || {}],
      allowedValues: [extendedVar.allowedValues || []],
      value: [defaultValue, validators],
       tableData: variable.varType === VariableType.TABLE ? 
      this.fb.array(this.initializeTableRows(extendedVar.config)) : this.fb.array([])
    });
  }

  private initializeTableData(config: any): any[] {
    if (!config?.columns) return [];
    
    // Tạo một dòng mặc định với các cột
    const defaultRow: any = {};
    config.columns.forEach((column: any) => {
      defaultRow[column.name] = '';
    });
    
    return [defaultRow];
  }

  private initializeTableRows(config: any): FormGroup[] {
    if (!config?.columns) return [];
    
    const defaultRow: any = {};
    config.columns.forEach((column: any) => {
      defaultRow[column.name] = '';
    });
    
    return [this.fb.group(defaultRow)];
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
      approverType: ['USER', Validators.required],
      employeeId: [null],
      positionId: [null],
      departmentId: [null],
      required: [true, Validators.required],
      isFinalStep: [false, Validators.required],
      action: ['APPROVE_ONLY', Validators.required],
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
        pos.clearValidators(); 
        pos.setValue(null, { emitEvent: false });
        dep.clearValidators(); 
        dep.setValue(null, { emitEvent: false });
      } else {
        emp.clearValidators(); 
        emp.setValue(null, { emitEvent: false });
        dep.setValidators(Validators.required);
        pos.setValidators(Validators.required);

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
        phCtrl.setValidators([ Validators.minLength(3)]);
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
      this.previewPdf();
    }
  }

  pdfLoading = false;
  pdfBlobUrl: string | null = null;
  previewOpen = false;
  pdfError: string | null = null;
  previewDownloadUrl: string | null = null;
  private cacheBust = Date.now();

  
  previewPdf(): void {
      if (!this.selectedTemplate) {
        this.toastr.error('Chưa chọn template');
        return;
      }
      const payload = this.buildPreviewRequest();
      this.pdfLoading = true;
      this.pdfError = null;

      this.contractService.previewContractPdf(payload).subscribe({
        next: (blob) => {
          try {
            this.pdfLoading = false;
            if (this.pdfBlobUrl) URL.revokeObjectURL(this.pdfBlobUrl);
            // ĐẢM BẢO MIME LÀ PDF
            const pdfBlob = new Blob([blob], { type: 'application/pdf' });
            this.pdfBlobUrl = URL.createObjectURL(pdfBlob);
            this.previewOpen = true;
          } catch (e) {
            this.pdfLoading = false;
            this.pdfError = 'Không hiển thị được PDF.';
            this.toastr.error(this.pdfError);
          }
        },
        error: (err) => {
          this.pdfLoading = false;
          this.pdfError = err?.error?.message || 'Không tạo được file xem trước';
          this.toastr.error(this.pdfError ?? 'Không tạo được file xem trước');
        }
      });
    }

  closePreview(): void {
    this.previewOpen = false;
    if (this.pdfBlobUrl) {
      URL.revokeObjectURL(this.pdfBlobUrl);
      this.pdfBlobUrl = null;
    }
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
// THAY THẾ TOÀN BỘ HÀM NÀY
createContract(submitNow: boolean): void {
  if (!this.validateStep(4)) return;

  this.isSaving = true;
  this.errorMessage = '';
  this.successMessage = '';

  const sigCfg: any = this.contractForm.get('signatureConfig')!.value;

  /** ========== LƯU NHÁP (KHÔNG SUBMIT) ========== */
  if (!submitNow) {
    if (sigCfg.flowOption === 'new') {
      // 1) Tạo flow mới -> 2) Dùng flowId trả về để tạo hợp đồng (draft)
      const flowReq = this.buildNewFlowRequest();
      if (!flowReq.steps?.length) {
        this.isSaving = false;
        this.toastr.error('Flow mới phải có ít nhất 1 bước.');
        return;
      }

      this.approvalFlowService.createFlow(flowReq).pipe(
        map((res: any) => res?.data?.id ?? res?.id ?? null),
        tap((id: number | null) => this.lastCreatedFlowId = id),
        switchMap((flowId: number | null) => {
          if (!flowId) throw new Error('Không lấy được ID luồng ký vừa tạo.');
          return this.contractService.createContract(this.buildCreateRequest(flowId));
        }),
        finalize(() => (this.isSaving = false))
      ).subscribe({
        next: () => {
          this.toastr.success('💾 Đã lưu nháp hợp đồng thành công!');
          this.currentStep = 1;
          if (this.selectedTemplate) this.loadVariablesForm(this.selectedTemplate);
        },
        error: (err) => {
          console.error('Save draft (new flow) error:', err);
          this.toastr.error('Không thể lưu nháp hợp đồng. Vui lòng thử lại.');
        }
      });
      return;
    }

    // flowOption = default / existing: gắn flowId ngay, không cần tạo flow
    const flowIdForContract =
      sigCfg.flowOption === 'default'
        ? ((this.defaultFlow?.id ?? this.selectedTemplate?.defaultFlowId) ?? null)
        : (sigCfg.existingFlowId ?? null);

    this.contractService.createContract(this.buildCreateRequest(flowIdForContract)).pipe(
      finalize(() => (this.isSaving = false))
    ).subscribe({
      next: () => {
        this.toastr.success('💾 Đã lưu nháp hợp đồng thành công!');
        this.currentStep = 1;
        if (this.selectedTemplate) this.loadVariablesForm(this.selectedTemplate);
      },
      error: (err) => {
        console.error('Save draft error:', err);
        this.toastr.error('Không thể lưu nháp hợp đồng. Vui lòng thử lại.');
      }
    });
    return;
  }

  /** ========== TẠO & TRÌNH KÝ ========== */
  let submitFlowId: number | null = null;
  let createFlowForSubmit$: Observable<number | null> = of(null);

  if (sigCfg.flowOption === 'new') {
    const flowReq = this.buildNewFlowRequest();
    if (!flowReq.steps?.length) {
      this.isSaving = false;
      this.toastr.error('Flow mới phải có ít nhất 1 bước.');
      return;
    }
    createFlowForSubmit$ = this.approvalFlowService.createFlow(flowReq).pipe(
      map((res: any) => res?.data?.id ?? res?.id ?? null),
      tap((id: number | null) => submitFlowId = id)
    );
  } else if (sigCfg.flowOption === 'default') {
    if (!this.defaultFlow?.id && !this.selectedTemplate?.defaultFlowId) {
      this.isSaving = false;
      this.toastr.error('Template chưa có luồng ký mặc định.');
      return;
    }
    if (!this.defaultFlow?.steps?.length) {
      this.isSaving = false;
      this.toastr.error('Luồng ký mặc định chưa có bước.');
      return;
    }
    submitFlowId = this.defaultFlow?.id ?? this.selectedTemplate?.defaultFlowId ?? null;
  } else if (sigCfg.flowOption === 'existing') {
    if (!sigCfg.existingFlowId) {
      this.isSaving = false;
      this.toastr.error('Vui lòng chọn luồng ký có sẵn.');
      return;
    }
    submitFlowId = sigCfg.existingFlowId;
  }

  createFlowForSubmit$.pipe(
    switchMap(() => this.contractService.createContract(this.buildCreateRequest(submitFlowId))),
    switchMap((res: any) => {
      const contractId = res?.data?.id as number | undefined;
      if (!contractId) throw new Error('Không lấy được contractId.');
      return this.contractApprovalService
        .submitForApproval(contractId, submitFlowId ?? undefined)
        .pipe(map(() => res));
    }),
    finalize(() => (this.isSaving = false))
  ).subscribe({
    next: () => {
      this.toastr.success('🎉 Đã tạo hợp đồng và trình ký thành công!');
      this.currentStep = 1;
      if (this.selectedTemplate) this.loadVariablesForm(this.selectedTemplate);
    },
    error: (err) => {
      console.error('Create & submit error:', err);
      this.toastr.error('Không thể tạo hoặc trình ký hợp đồng. Vui lòng thử lại.');
    }
  });
}

/** Build payload tạo flow từ Form (hỗ trợ signaturePlaceholder) */
private buildNewFlowRequest(): ApprovalFlowRequest {
  const sigCfg: any = this.contractForm.get('signatureConfig')!.value;

  const steps: any[] = this.newSignersFormArray.controls.map((ctrl, idx) => {
    const v = (ctrl as FormGroup).value;

    const step: any = {
      stepOrder: idx + 1,
      required: !!v.required,
      isFinalStep: !!v.isFinalStep,
      approverType: v.approverType as ApproverType,
      action: v.action as ApprovalAction,
      employeeId: v.approverType === 'USER' ? v.employeeId : undefined,
      positionId: v.approverType === 'POSITION' ? v.positionId : undefined,
      departmentId: v.approverType === 'POSITION' ? v.departmentId : undefined
    };

    // Nếu là bước ký thì gửi kèm placeholder
    if (v.action !== 'APPROVE_ONLY') {
      step.signaturePlaceholder = v.signaturePlaceholder || 'SIGN';
    }
    return step;
  });

  return {
    name: (sigCfg.flowName || `Flow phê duyệt - ${this.contractForm.get('contractName')?.value || 'Không tên'}`).trim(),
    description: sigCfg.flowDescription || '',
    templateId: this.selectedTemplate?.id as number,
    steps
  };
}


  // Helper methods để render các control khác nhau
  isTextField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    const type = variableGroup.get('varType')?.value;
    return type === 'STRING' || type === 'TEXT' || type === 'TEXTAREA';
  }

  isNumberField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    return variableGroup.get('varType')?.value === 'NUMBER';
  }

  isDateField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    return variableGroup.get('varType')?.value === 'DATE';
  }

  isBooleanField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    return variableGroup.get('varType')?.value === 'BOOLEAN';
  }

  isDropdownField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    return variableGroup.get('varType')?.value === 'DROPDOWN';
  }

  isListField(control: AbstractControl): boolean {
    const variableGroup = control as FormGroup;
    return variableGroup.get('varType')?.value === 'LIST';
  }

  getDropdownOptions(control: AbstractControl): string[] {
    const variableGroup = control as FormGroup;
    return variableGroup.get('config')?.value?.options || [];
  }

  getListItems(control: AbstractControl): string[] {
    const variableGroup = control as FormGroup;
    return variableGroup.get('config')?.value?.items || [];
  }

  getTableColumns(control: AbstractControl): any[] {
    const variableGroup = control as FormGroup;
    return variableGroup.get('config')?.value?.columns || [];
  }

  getBooleanLabels(control: AbstractControl): { trueLabel: string; falseLabel: string } {
    const variableGroup = control as FormGroup;
    const config = variableGroup.get('config')?.value || {};
    return {
      trueLabel: config.trueLabel || 'Có',
      falseLabel: config.falseLabel || 'Không'
    };
  }

  getTextareaRows(control: AbstractControl): number {
    const variableGroup = control as FormGroup;
    return variableGroup.get('config')?.value?.rows || 4;
  }

  private createEmptyTableRow(control: AbstractControl): any {
    const variableGroup = control as FormGroup;
    const columns = this.getTableColumns(variableGroup);
    const row: any = {};
    columns.forEach((column: any) => {
      row[column.name] = '';
    });
    return row;
  }

  // Helper methods khác
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
    this.currentPage = 1;
  }

  getInputType(variableType: string): string {
    const types: any = { 'TEXT': 'text', 'NUMBER': 'number', 'DATE': 'date' };
    return types[variableType] || 'text';
  }
  
  getCategoryLabel(category: string): string {
    const labels: any = { 'labor': 'Lao động', 'sale': 'Mua bán', 'rental': 'Thuê nhà', 'service': 'Dịch vụ' };
    return labels[category] || category;
  }
  
  getTypeLabel(type: VariableType): string {
    const labels: { [key in VariableType]: string } = { 
      [VariableType.TEXT]: 'Văn bản', 
      [VariableType.TEXTAREA]: 'Văn bản dài', 
      [VariableType.NUMBER]: 'Số', 
      [VariableType.DATE]: 'Ngày tháng',
      [VariableType.BOOLEAN]: 'True/False',
      [VariableType.DROPDOWN]: 'Dropdown',
      [VariableType.LIST]: 'Danh sách',
      [VariableType.TABLE]: 'Bảng'
    };
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
      const v = (ctrl as FormGroup).value;
      let varValue: any = v.value;
      
      switch (v.varType) {
        case VariableType.BOOLEAN:
          varValue = v.value ? 'true' : 'false';
          break;
        case VariableType.TABLE:
          // SỬA LỖI: Lấy dữ liệu từ tableData thay vì value
          const tableData = ctrl.get('tableData')?.value || [];
          varValue = JSON.stringify(tableData);
          break;
        case VariableType.NUMBER:
          varValue = v.value !== null && v.value !== '' ? v.value.toString() : '';
          break;
        default:
          varValue = v.value !== null && v.value !== undefined ? v.value.toString() : '';
      }
      
      return {
        varName: v.varName,
        varValue: varValue
      } as VariableValueRequest;
    });
  }

  /** Tạo payload Preview (không bắt buộc flowId) */
  private buildPreviewRequest(): CreateContractRequest {
    return {
      templateId: this.selectedTemplate?.id as number,
      title: this.contractForm.get('contractName')?.value ?? '',
      variables: this.buildVariablePayload(),
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

  // Load flow default
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

  // ====== CÁC PHƯƠNG THỨC XỬ LÝ TABLE ======

    // Lấy FormArray của table data
    getTableData(variableGroup: AbstractControl): FormArray {
      return variableGroup.get('tableData') as FormArray;
    }

    // Thêm dòng mới vào table
    addTableRow(variableGroup: AbstractControl): void {
      const tableData = this.getTableData(variableGroup);
      const columns = this.getTableColumns(variableGroup);
      
      const newRow: any = {};
      columns.forEach((column: any) => {
        newRow[column.name] = '';
      });
      
      // Sử dụng patchValue để thêm row mới mà không re-render toàn bộ
      tableData.push(this.fb.group(newRow));
      
      // Cập nhật giá trị với debounce
      this.debounceTableUpdate(variableGroup);
    }

    // Xóa dòng khỏi table
    removeTableRow(variableGroup: AbstractControl, rowIndex: number): void {
      const tableData = this.getTableData(variableGroup);
      if (tableData.length > 1) {
        tableData.removeAt(rowIndex);
        this.updateTableValue(variableGroup); // Cập nhật ngay lập tức khi xóa
      }
    }

    // Cập nhật giá trị table từ FormArray sang biến chính
    updateTableValue(variableGroup: AbstractControl): void {
      const tableData = this.getTableData(variableGroup);
      const value = tableData.value;
      
      // Sử dụng patchValue thay vì setValue để tránh mất focus
      variableGroup.patchValue({
        value: value
      }, { emitEvent: false }); // emitEvent: false để tránh vòng lặp
    }
    // Lấy danh sách các dòng table
    getTableRows(variableGroup: AbstractControl): any[] {
      const tableData = this.getTableData(variableGroup);
      return tableData.controls.map(control => control.value);
    }
    // Kiểm tra xem có phải là table không
    isTableField(control: AbstractControl): boolean {
      const variableGroup = control as FormGroup;
      return variableGroup.get('varType')?.value === VariableType.TABLE;
    }

    onTableInput(variableGroup: AbstractControl, rowIndex: number, columnName: string, event: any): void {
      const value = event.target.value;
      
      // Cập nhật giá trị ngay lập tức mà không cần đợi blur
      const tableData = this.getTableData(variableGroup);
      const row = tableData.at(rowIndex) as FormGroup;
      row.get(columnName)?.setValue(value, { emitEvent: false });
      
      // Debounce để cập nhật giá trị chính (tránh performance issues)
      this.debounceTableUpdate(variableGroup);
    }

    // Biến để debounce
    private tableUpdateTimeout: any;

    private debounceTableUpdate(variableGroup: AbstractControl): void {
      // Clear timeout cũ
      if (this.tableUpdateTimeout) {
        clearTimeout(this.tableUpdateTimeout);
      }
      
      // Set timeout mới
      this.tableUpdateTimeout = setTimeout(() => {
        this.updateTableValue(variableGroup);
      }, 300); // 300ms debounce
    }

      trackByTemplateId = (_: number, t: ContractTemplateResponse) => t?.id;
      trackByIndex = (index: number) => index;
      trackByStepOrder = (_: number, s: { stepOrder?: number }) => s?.stepOrder ?? _;
      trackByTableRow(index: number, item: any): number {
        return index;
      }
    }