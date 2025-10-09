import { Component, ElementRef, ViewChild, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { CategoryService } from '../../core/services/category.service';
import { 
  TemplatePreviewResponse, 
  TemplateVariablePreview,
  TemplateVariableRequest,
  VariableConfig 
} from '../../core/models/template-preview-response.model';
import { ContractTemplateResponse } from '../../core/models/contract-template-response.model';
import { VariableUpdateRequest } from '../../core/models/variable-update-request.model';
import { ContractTemplateCreateRequest } from '../../core/models/ontract-template-create-request.model';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';  
import { Category } from '../../core/models/category.model';

interface VariableDraft extends TemplateVariablePreview {
  required: boolean;
  name: string;
  config: VariableConfig;
}


@Component({
  selector: 'app-contract-template',
  standalone: true,                            
  imports: [CommonModule, FormsModule],
  templateUrl: './contract-template.component.html',
  styleUrls: ['./contract-template.component.scss']
})
export class ContractTemplateComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;

  activeTab: 'file' | 'link' = 'file';
  selectedFile: File | null = null;
  docLink = '';
  accountId: number | null = null;

  isLoading = false;
  progress = 0;

  maxFileMB = 5;
  dragActive = false;
  inlineError = '';

  // Preview
  previewResponse?: TemplatePreviewResponse;
  extractedVariables: VariableDraft[] = [];

  // Finalize
  finalizedTemplate?: ContractTemplateResponse;
  templateName = '';
  templateDescription = '';
  selectedCategoryId: number | null = null;

  // Categories
  categories: Category[] = [];
  isLoadingCategories = false;

  // UI state
  currentStep = 1;
  successMessage = '';
  errorMessage = '';

  constructor(
    private contractTemplateService: ContractTemplateService,
    private categoryService: CategoryService,
    private toastr: ToastrService,
    private router: Router 
  ) {}

  ngOnInit(): void {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      this.accountId = user.id; 
    }
    this.loadCategories(); 
  }

  // Load categories
  loadCategories(): void {
    this.isLoadingCategories = true;
    this.categoryService.getAllCategories().subscribe({
      next: (response: any) => {
        if (response && response.data && Array.isArray(response.data)) {
          this.categories = response.data;
        } else {
          console.warn('Unexpected categories response format:', response);
          this.categories = [];
        }
        this.isLoadingCategories = false;
      },
      error: (error) => {
        console.error('Lỗi load categories:', error);
        this.isLoadingCategories = false;
        this.toastr.error('Không thể tải danh sách danh mục');
        this.categories = [];
      }
    });
  }

  // Chuyển tab
  setActiveTab(tab: 'file' | 'link'): void {
    this.activeTab = tab;
    this.resetMessages();
    this.selectedFile = null;
    this.docLink = '';
  }

  // Upload file
  onFileSelected(event: any): void {
    const file = event.target.files?.[0];
    if (!file) return;
    this.validateAndSetFile(file);
  }

  triggerFileInput(): void {
    this.fileInput?.nativeElement.click();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onFileDropped(event: DragEvent): void {
    event.preventDefault();
    this.dragActive = false;
    const file = event.dataTransfer?.files?.[0];
    if (!file) return;
    this.validateAndSetFile(file);
  }

  private validateAndSetFile(file: File) {
    this.inlineError = '';
    this.errorMessage = '';

    const isDocx = /\.docx$/i.test(file.name);
    if (!isDocx) {
      this.inlineError = 'Vui lòng chọn đúng tệp .docx';
      this.toastr.error(this.inlineError);
      return;
    }

    const maxBytes = this.maxFileMB * 1024 * 1024;
    if (file.size > maxBytes) {
      this.inlineError = `Kích thước tệp vượt quá ${this.maxFileMB}MB`;
      this.toastr.error(this.inlineError);
      return;
    }

    this.selectedFile = file;
    this.toastr.success('Đã chọn file hợp lệ');
  }

  // Preview
  previewTemplate(): void {
    this.resetMessages();
    this.inlineError = '';
    this.isLoading = true;
    this.progress = 10;

    if (this.activeTab === 'file') {
      if (!this.selectedFile) {
        this.isLoading = false;
        this.inlineError = 'Vui lòng chọn file .docx';
        this.toastr.error(this.inlineError);
        return;
      }
      this.contractTemplateService.previewFromFile(this.selectedFile).subscribe({
        next: (res) => this.handlePreviewSuccess(res),
        error: (err) => this.handleError(err)
      });
    } else {
      if (!this.docLink.trim()) {
        this.isLoading = false;
        this.inlineError = 'Vui lòng nhập link Google Docs';
        this.toastr.error(this.inlineError);
        return;
      }
      this.contractTemplateService.previewFromLink(this.docLink).subscribe({
        next: (res) => this.handlePreviewSuccess(res),
        error: (err) => this.handleError(err)
      });
    }
  }

  private handlePreviewSuccess(response: TemplatePreviewResponse): void {
    this.isLoading = false;
    this.progress = 100;

    this.previewResponse = response;
    
    // Đảm bảo config luôn được khởi tạo
    this.extractedVariables = (response.variables || []).map((v) => {
      const defaultConfig = this.getDefaultConfig(v.varType);
      return {
        ...v,
        varType: v.varType || 'STRING',
        required: false, // Giá trị mặc định
        name: '', // Giá trị mặc định
        config: v.config || defaultConfig,
        allowedValues: v.allowedValues || this.getAllowedValuesFromConfig(v.varType, defaultConfig)
      };
    });

    if (this.extractedVariables.length) {
      this.currentStep = 2;
      this.successMessage = `Đã trích xuất ${this.extractedVariables.length} biến`;
      this.toastr.success(this.successMessage);
    } else {
      this.inlineError = 'Không tìm thấy biến nào trong hợp đồng, bạn vẫn có thể tiếp tục.';
      this.currentStep = 2;
    }
  }

  private getAllowedValuesFromConfig(varType: string, config: VariableConfig): string[] {
    switch (varType) {
      case 'DROPDOWN':
        return config.options ? [...config.options] : [];
      case 'LIST':
        return config.items ? [...config.items] : [];
      default:
        return [];
    }
  }

  // Lấy config mặc định theo kiểu biến
  private getDefaultConfig(varType: string): VariableConfig {
    switch (varType) {
      case 'BOOLEAN':
        return { trueLabel: 'Có', falseLabel: 'Không' };
      case 'DROPDOWN':
        return { options: ['Option 1', 'Option 2'] };
      case 'LIST':
        return { items: ['Item 1', 'Item 2'] };
      case 'TABLE':
        return { 
          tableName: 'table_data',
          columns: [
            { name: 'column_1', type: 'STRING' },
            { name: 'column_2', type: 'STRING' }
          ]
        };
      case 'TEXTAREA':
        return { rows: 4 };
      case 'NUMBER':
        return { min: 0, max: 1000000 };
      default:
        return {};
    }
  }

  // Xử lý khi thay đổi kiểu dữ liệu
  onVariableTypeChange(variable: VariableDraft) {
    variable.config = this.getDefaultConfig(variable.varType);
    variable.allowedValues = [];
    
    if (variable.varType === 'DROPDOWN' && variable.config.options) {
      variable.allowedValues = [...variable.config.options];
    } else if (variable.varType === 'LIST' && variable.config.items) {
      variable.allowedValues = [...variable.config.items];
    }
  }

  getBooleanConfig(variable: VariableDraft) {
    return {
      trueLabel: variable.config?.trueLabel || 'Có',
      falseLabel: variable.config?.falseLabel || 'Không'
    };
  }
  getDropdownConfig(variable: VariableDraft) {
    return {
      options: variable.config?.options || ['Option 1', 'Option 2']
    };
  }

  getTableConfig(variable: VariableDraft) {
    return {
      tableName: variable.config?.tableName || 'table_data',
      columns: variable.config?.columns || [
        { name: 'column_1', type: 'STRING' },
        { name: 'column_2', type: 'STRING' }
      ]
    };
  }

  getListConfig(variable: VariableDraft) {
    return {
      items: variable.config?.items || ['Item 1', 'Item 2']
    };
  }

  getTextareaConfig(variable: VariableDraft) {
    return {
      rows: variable.config?.rows || 4
    };
  }

  getNumberConfig(variable: VariableDraft) {
    return {
      min: variable.config?.min || 0,
      max: variable.config?.max || 1000000
    };
  }

  // Quản lý options cho Dropdown
  addOption(variable: VariableDraft) {
    if (!variable.config.options) {
      variable.config.options = [];
    }
    variable.config.options.push(`Option ${variable.config.options.length + 1}`);
    variable.allowedValues = [...variable.config.options];
  }

  removeOption(variable: VariableDraft, index: number) {
    if (variable.config.options && variable.config.options.length > 1) {
      variable.config.options.splice(index, 1);
      variable.allowedValues = [...variable.config.options];
    }
  }

  // Quản lý items cho List
  addListItem(variable: VariableDraft) {
    if (!variable.config.items) {
      variable.config.items = [];
    }
    variable.config.items.push(`Item ${variable.config.items.length + 1}`);
    variable.allowedValues = [...variable.config.items];
  }

  removeListItem(variable: VariableDraft, index: number) {
    if (variable.config.items && variable.config.items.length > 1) {
      variable.config.items.splice(index, 1);
      variable.allowedValues = [...variable.config.items];
    }
  }

  // Quản lý columns cho Table
  addColumn(variable: VariableDraft) {
    if (!variable.config.columns) {
      variable.config.columns = [];
    }
    variable.config.columns.push({ 
      name: `column_${variable.config.columns.length + 1}`, 
      type: 'STRING' 
    });
  }

  removeColumn(variable: VariableDraft, index: number) {
    if (variable.config.columns && variable.config.columns.length > 1) {
      variable.config.columns.splice(index, 1);
    }
  }

  isFieldNameValid(): boolean {
    return this.extractedVariables.every(v => v.name?.trim().length > 0);
  }

  // Tạo mới template
  onCreateOrUpdateTemplate(): void {
    if (!this.templateName.trim()) {
      this.toastr.error('Tên template là bắt buộc.');
      return;
    }

    if (!this.isFieldNameValid()) {
      this.toastr.error('Vui lòng nhập tên cho tất cả các biến.');
      return;
    }

    // Tạo danh sách biến theo interface TemplateVariableRequest
    const variables: TemplateVariableRequest[] = this.extractedVariables.map(v => ({
      varName: v.varName,
      varType: v.varType,
      required: v.required,
      name: v.name.trim(),
      orderIndex: v.orderIndex,
      config: v.config,
      allowedValues: v.allowedValues
    }));

    const request: ContractTemplateCreateRequest = {
      tempFileName: this.previewResponse?.tempFileName || '',
      name: this.templateName,
      description: this.templateDescription,
      categoryId: this.selectedCategoryId,
      variables: variables
    };

    this.contractTemplateService.finalizeTemplate(request).subscribe({
      next: (template) => {
        this.finalizedTemplate = template;
        this.successMessage = 'Template đã tạo thành công';
        this.toastr.success(this.successMessage);
        this.router.navigate(['/contract/templates/list']);
      },
      error: (err) => {
        this.handleError(err);
      }
    });
  }

  private handleError(err: any): void {
    this.isLoading = false;
    this.progress = 0;
    const msg = err?.error?.message || err?.message || 'Có lỗi xảy ra';
    this.errorMessage = msg;
    this.inlineError = msg;
    this.toastr.error(msg);
  }

  public resetMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
    this.progress = 0;
  }

  formatBytes(bytes: number): string {
    if (!bytes) return '0 B';
    const sizes = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log(bytes)/Math.log(1024));
    return `${(bytes/Math.pow(1024,i)).toFixed(1)} ${sizes[i]}`;
  }

  removeSelectedFile(e?: Event): void {
    e?.stopPropagation();
    this.selectedFile = null;
    this.inlineError = '';
    this.resetMessages();
    if (this.fileInput?.nativeElement) this.fileInput.nativeElement.value = '';
  }

  onDragEnter(e: DragEvent) { e.preventDefault(); this.dragActive = true; }
  onDragLeave(e: DragEvent) { e.preventDefault(); this.dragActive = false; }

  getCategoryName(categoryId: number): string {
    const category = this.categories.find(c => c.id === categoryId);
    return category ? category.name : 'Không phân loại';
  }
}