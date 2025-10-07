import { Component, ElementRef, ViewChild, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ContractTemplateService } from '../../core/services/contract-template.service';
import { TemplatePreviewResponse, TemplateVariablePreview } from '../../core/models/template-preview-response.model';
import { ContractTemplateResponse } from '../../core/models/contract-template-response.model';
import { VariableUpdateRequest } from '../../core/models/variable-update-request.model';
import { ContractTemplateCreateRequest } from '../../core/models/ontract-template-create-request.model';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';  

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

  maxFileMB = 5;                 // giới hạn dung lượng
  dragActive = false;            // highlight dropzone
  inlineError = '';              // lỗi hiển thị ngay dưới ô

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

  constructor(private contractTemplateService: ContractTemplateService,
    private toastr: ToastrService,
     private router: Router 
  ) {}

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

    // validate đuôi .docx
    const isDocx = /\.docx$/i.test(file.name);
    if (!isDocx) {
      this.inlineError = 'Vui lòng chọn đúng tệp .docx';
      this.toastr.error(this.inlineError);
      return;
    }

    // validate size
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
    this.extractedVariables = (response.variables || []).map((v) => ({
      ...v,
      varType: 'TEXT',
      required: false,
      name: '',
    }));

    if (this.extractedVariables.length) {
      this.currentStep = 2;
      this.successMessage = `Đã trích xuất ${this.extractedVariables.length} biến`;
      this.toastr.success(this.successMessage);
    } else {
      this.inlineError = 'Không tìm thấy biến nào trong hợp đồng, bạn vẫn có thể tiếp tục.';
      this.currentStep = 2;
    }
  }

 isFieldNameValid(): boolean {
    return this.extractedVariables.every(v => v.name.trim().length > 0);
  }

  // Tạo mới / Cập nhật
  onCreateOrUpdateTemplate(): void {
    if (!this.templateName.trim()) {
      this.toastr.error('Tên template là bắt buộc.');
      return;
    }

    const request: ContractTemplateCreateRequest = {
      tempFileName: this.previewResponse?.tempFileName || '',
      name: this.templateName,
      description: this.templateDescription,
      variables: this.extractedVariables.map(v => ({
        varName: v.varName,
        varType: v.varType || 'TEXT',
        required: v.required,
        name: v.name.trim(),
        orderIndex: v.orderIndex
      }))
    };

    this.contractTemplateService.finalizeTemplate(request).subscribe({
      next: (template) => {
        this.finalizedTemplate = template;
        this.successMessage = 'Template đã tạo thành công';
        this.toastr.success(this.successMessage);

        // Chuyển hướng đến danh sách template sau khi tạo thành công
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

}
