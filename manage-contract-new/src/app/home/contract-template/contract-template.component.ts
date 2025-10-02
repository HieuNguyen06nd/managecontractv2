import { Component, ElementRef, ViewChild, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { TemplatePreviewResponse, TemplateVariablePreview } from '../../core/models/template-preview-response.model';
import { ContractTemplateResponse } from '../../core/models/contract-template-response.model';
import { VariableUpdateRequest } from '../../core/models/variable-update-request.model';
import { ContractTemplateCreateRequest } from '../../core/models/ontract-template-create-request.model';

type VariableDraft = TemplateVariablePreview & { varType: string; required: boolean ;name: string};

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

  // Preview
  previewResponse?: TemplatePreviewResponse;
  extractedVariables: VariableDraft[] = [];

  // Finalize
  finalizedTemplate?: ContractTemplateResponse;
  templateName = '';
  templateDescription = '';

  // UI state
  currentStep = 1; // 1: upload, 2: chọn biến + nhập thông tin, 3: hoàn thành
  successMessage = '';
  errorMessage = '';

  constructor(private contractTemplateService: ContractTemplateService) {}

  ngOnInit(): void {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      const user = JSON.parse(userStr);
      this.accountId = user.id; // nếu BE cần
    }
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
    if (file && file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
      this.selectedFile = file;
      this.resetMessages();
    } else {
      this.errorMessage = 'Vui lòng chọn file DOCX';
    }
  }

  triggerFileInput(): void {
    this.fileInput?.nativeElement.click();
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onFileDropped(event: DragEvent): void {
    event.preventDefault();
    const file = event.dataTransfer?.files?.[0];
    if (!file) return;

    if (file.type === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
      this.selectedFile = file;
      this.resetMessages();
    } else {
      this.errorMessage = 'Vui lòng chọn file DOCX';
    }
  }

  // Preview
  previewTemplate(): void {
    this.resetMessages();
    this.isLoading = true;

    if (this.activeTab === 'file') {
      if (!this.selectedFile) {
        this.isLoading = false;
        this.errorMessage = 'Vui lòng chọn file';
        return;
      }
      this.contractTemplateService.previewFromFile(this.selectedFile).subscribe({
        next: (res) => this.handlePreviewSuccess(res),
        error: (err) => this.handleError(err)
      });
    } else {
      if (!this.docLink.trim()) {
        this.isLoading = false;
        this.errorMessage = 'Vui lòng nhập link Google Docs';
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
    // ✅ thêm varType mặc định là 'TEXT' cho đồng bộ BE
    this.extractedVariables = (response.variables || []).map((v) => ({
      ...v,
      varType: 'TEXT',
      required: false,
      name: '',
    }));

    if (this.extractedVariables.length) {
      this.currentStep = 2;
      this.successMessage = `Đã trích xuất ${this.extractedVariables.length} biến`;
    } else {
      this.errorMessage = 'Không tìm thấy biến nào trong hợp đồng';
    }
  }

 isFieldNameValid(): boolean {
    return this.extractedVariables.every(v => v.name.trim().length > 0);
  }

  // Tạo mới / Cập nhật
  onCreateOrUpdateTemplate(): void {
    if (!this.previewResponse) return;

    if (!this.finalizedTemplate) {
      // Tạo mới template
      const request: ContractTemplateCreateRequest = {
        tempFileName: this.previewResponse.tempFileName,
        name: this.templateName,
        description: this.templateDescription,
        variables: this.extractedVariables.map((v) => ({
          varName: v.varName,
          varType: (v.varType || 'TEXT').toUpperCase(),
          required: v.required,
          name: v.name.trim(),
          orderIndex: v.orderIndex
        }))
      };

      this.contractTemplateService.finalizeTemplate(request).subscribe({
        next: (template) => {
          this.finalizedTemplate = template;
          this.currentStep = 3;
          this.successMessage = 'Template đã tạo thành công';
        },
        error: (err) => this.handleError(err)
      });
    } else {
      // Cập nhật biến
      const payload: VariableUpdateRequest[] = this.extractedVariables.map((v) => ({
        varName: v.varName,
        varType: (v.varType || 'TEXT').toUpperCase(),
        required: v.required,
        name: v.name.trim()
      }));

      this.contractTemplateService.updateVariables(this.finalizedTemplate.id, payload).subscribe({
        next: () => {
          this.successMessage = 'Cập nhật biến thành công';
          this.currentStep = 3;
        },
        error: (err) => this.handleError(err)
      });
    }
  }

  private handleError(err: any): void {
    this.isLoading = false;
    this.errorMessage = err?.error?.message || err?.message || 'Có lỗi xảy ra';
  }

  private resetMessages(): void {
    this.successMessage = '';
    this.errorMessage = '';
    this.progress = 0;
  }
}
