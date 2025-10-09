import { Component, OnInit, OnDestroy, ElementRef, ViewChild, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';
import { EmployeeService } from '../../core/services/employee.service';
import { AuthProfileResponse, StatusUser, RoleResponse } from '../../core/models/auth.model';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, HttpClientModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('signatureCanvas') signatureCanvas!: ElementRef<HTMLCanvasElement>;
  
  private destroy$ = new Subject<void>();
  
  // Profile data
  profile: AuthProfileResponse = {
    id: 0,
    fullName: '',
    phone: '',
    email: '',
    avatarImage: '',
    signatureImage: '',
    department: '',
    position: '',
    status: 'ACTIVE' as any,
    roles: [],
    dateCreated: ''
  };

  // Form data
  formData = {
    fullName: '',
    email: '',
    phone: '',
    position: '',
    department: '',
    joinDate: '',
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  };

  // UI state
  activeTab: string = 'info';
  selectedSignatureMethod: string = '';
  isDrawing = false;
  lastX = 0;
  lastY = 0;
  ctx!: CanvasRenderingContext2D;
  tempSignatureImage: string = ''; // Ảnh chữ ký tạm thời khi upload
  tempAvatarImage: string = ''; // Ảnh avatar tạm thời khi upload
  baseUrl: string = 'http://localhost:8080'; // URL base của backend

  // Loading states
  isUploadingAvatar = false;
  isUploadingSignature = false;
  isSavingProfile = false;

  // Activity data
  stats = {
    contractsCreated: 24,
    contractsSigned: 18,
    contractsPending: 6,
    contractsApproved: 15
  };

  constructor(private employeeService: EmployeeService) {}

  ngOnInit(): void {
    this.loadProfile();
  }

  ngAfterViewInit(): void {
    this.initCanvas();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadProfile(): void {
    this.employeeService.getMyProfile()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          console.log('Profile loaded:', response);
          this.profile = response.data;
          this.populateFormData();
        },
        error: (error) => {
          console.error('Error loading profile:', error);
        }
      });
  }

  populateFormData(): void {
    this.formData = {
      fullName: this.profile.fullName,
      email: this.profile.email,
      phone: this.profile.phone,
      position: this.profile.position || '',
      department: this.profile.department || '',
      joinDate: this.formatDateForInput(this.profile.dateCreated),
      currentPassword: '',
      newPassword: '',
      confirmPassword: ''
    };
  }

  formatDateForInput(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toISOString().split('T')[0];
  }

  // Xử lý URL ảnh
  getFullImageUrl(relativePath: string | undefined | null): string {
    if (!relativePath) {
      return 'https://via.placeholder.com/120?text=No+Image';
    }
    
    if (relativePath.startsWith('http')) {
      return relativePath;
    }
    
    return `${this.baseUrl}/uploads/${relativePath}`;
  }

  // Kiểm tra có chữ ký không
  hasSignature(): boolean {
    return !!this.profile.signatureImage;
  }

  // Tab management
  switchTab(tab: string): void {
    this.activeTab = tab;
    if (tab === 'signature') {
      setTimeout(() => this.initCanvas(), 100);
    }
  }

  // ========== AVATAR HANDLING ==========
  triggerAvatarUpload(): void {
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = 'image/*';
    fileInput.onchange = (event) => this.handleAvatarUpload(event);
    fileInput.click();
  }

  handleAvatarUpload(event: any): void {
    const file = event.target.files[0];
    if (file) {
      // Validate file type
      const allowedTypes = ['image/jpeg', 'image/png', 'image/gif'];
      if (!allowedTypes.includes(file.type)) {
        alert('Chỉ chấp nhận file ảnh (JPG, PNG, GIF)');
        return;
      }

      // Validate file size (max 5MB)
      if (file.size > 5 * 1024 * 1024) {
        alert('Kích thước file không được vượt quá 5MB');
        return;
      }

      // Hiển thị preview trước
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.tempAvatarImage = e.target.result;
      };
      reader.readAsDataURL(file);
    }
  }

  // Lưu avatar đã chọn
  saveAvatar(): void {
    if (!this.tempAvatarImage) {
      alert('Vui lòng chọn ảnh đại diện trước!');
      return;
    }

    this.isUploadingAvatar = true;

    // Chuyển base64 thành blob để upload
    fetch(this.tempAvatarImage)
      .then(res => res.blob())
      .then(blob => {
        const file = new File([blob], 'avatar.png', { type: 'image/png' });
        
        this.employeeService.uploadAvatar(file)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: (response) => {
              this.isUploadingAvatar = false;
              this.loadProfile(); // Tải lại profile để lấy avatar mới
              this.tempAvatarImage = ''; // Xóa preview tạm
              alert('Ảnh đại diện đã được cập nhật thành công!');
            },
            error: (error) => {
              this.isUploadingAvatar = false;
              console.error('Error uploading avatar:', error);
              alert('Có lỗi xảy ra khi tải lên ảnh đại diện: ' + (error.error?.message || error.message));
            }
          });
      });
  }

  // Hủy thay đổi avatar
  cancelAvatarChange(): void {
    this.tempAvatarImage = '';
  }

  // ========== SIGNATURE HANDLING ==========
  selectSignatureMethod(method: string): void {
    this.selectedSignatureMethod = method;
    if (method === 'draw') {
      setTimeout(() => this.initCanvas(), 100);
    }
  }

  initCanvas(): void {
    if (!this.signatureCanvas?.nativeElement) return;
    
    const canvas = this.signatureCanvas.nativeElement;
    this.ctx = canvas.getContext('2d')!;
    
    // Set canvas size
    canvas.width = canvas.offsetWidth;
    canvas.height = canvas.offsetHeight;
    
    // Styles
    this.ctx.lineWidth = 2;
    this.ctx.lineCap = 'round';
    this.ctx.lineJoin = 'round';
    this.ctx.strokeStyle = '#000';
    
    // Event listeners
    canvas.addEventListener('mousedown', this.startDrawing.bind(this));
    canvas.addEventListener('mousemove', this.draw.bind(this));
    canvas.addEventListener('mouseup', this.stopDrawing.bind(this));
    canvas.addEventListener('mouseout', this.stopDrawing.bind(this));
    
    // Touch events for mobile
    canvas.addEventListener('touchstart', this.handleTouchStart.bind(this));
    canvas.addEventListener('touchmove', this.handleTouchMove.bind(this));
    canvas.addEventListener('touchend', this.stopDrawing.bind(this));
  }

  startDrawing(e: MouseEvent): void {
    this.isDrawing = true;
    [this.lastX, this.lastY] = [e.offsetX, e.offsetY];
  }

  draw(e: MouseEvent): void {
    if (!this.isDrawing) return;
    this.ctx.beginPath();
    this.ctx.moveTo(this.lastX, this.lastY);
    this.ctx.lineTo(e.offsetX, e.offsetY);
    this.ctx.stroke();
    [this.lastX, this.lastY] = [e.offsetX, e.offsetY];
  }

  stopDrawing(): void {
    this.isDrawing = false;
  }

  // Touch event handlers
  handleTouchStart(e: TouchEvent): void {
    e.preventDefault();
    const touch = e.touches[0];
    const mouseEvent = new MouseEvent('mousedown', {
      clientX: touch.clientX,
      clientY: touch.clientY
    });
    this.startDrawing(mouseEvent as any);
  }

  handleTouchMove(e: TouchEvent): void {
    e.preventDefault();
    const touch = e.touches[0];
    const mouseEvent = new MouseEvent('mousemove', {
      clientX: touch.clientX,
      clientY: touch.clientY
    });
    this.draw(mouseEvent as any);
  }

  clearCanvas(): void {
    if (this.ctx && this.signatureCanvas) {
      const canvas = this.signatureCanvas.nativeElement;
      this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
  }

  saveSignature(): void {
    if (!this.signatureCanvas) return;
    
    const canvas = this.signatureCanvas.nativeElement;
    
    // Kiểm tra xem có chữ ký trên canvas không
    const blank = document.createElement('canvas');
    blank.width = canvas.width;
    blank.height = canvas.height;
    
    if (canvas.toDataURL() === blank.toDataURL()) {
      alert('Vui lòng vẽ chữ ký trước khi lưu!');
      return;
    }
    
    const dataURL = canvas.toDataURL('image/png');
    
    this.employeeService.uploadSignatureBase64(dataURL)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (response) => {
          this.loadProfile(); // Tải lại profile để lấy chữ ký mới
          this.clearCanvas();
          this.selectedSignatureMethod = '';
          alert('Chữ ký đã được lưu thành công!');
        },
        error: (error) => {
          console.error('Error saving signature:', error);
          alert('Có lỗi xảy ra khi lưu chữ ký: ' + (error.error?.message || error.message));
        }
      });
  }

  triggerSignatureUpload(): void {
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = 'image/*';
    fileInput.onchange = (event) => this.handleSignatureUpload(event);
    fileInput.click();
  }

  handleSignatureUpload(event: any): void {
    const file = event.target.files[0];
    if (file) {
      // Validate file type
      const allowedTypes = ['image/jpeg', 'image/png', 'image/gif'];
      if (!allowedTypes.includes(file.type)) {
        alert('Chỉ chấp nhận file ảnh (JPG, PNG, GIF)');
        return;
      }

      // Validate file size (max 5MB)
      if (file.size > 5 * 1024 * 1024) {
        alert('Kích thước file không được vượt quá 5MB');
        return;
      }

      // Hiển thị preview trước
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.tempSignatureImage = e.target.result;
      };
      reader.readAsDataURL(file);
    }
  }

  // Lưu chữ ký đã upload
  saveUploadedSignature(): void {
    if (!this.tempSignatureImage) {
      alert('Vui lòng chọn ảnh chữ ký trước!');
      return;
    }

    this.isUploadingSignature = true;

    // Chuyển base64 thành blob để upload
    fetch(this.tempSignatureImage)
      .then(res => res.blob())
      .then(blob => {
        const file = new File([blob], 'signature.png', { type: 'image/png' });
        
        this.employeeService.uploadSignature(file)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: (response) => {
              this.isUploadingSignature = false;
              this.loadProfile(); // Tải lại profile để lấy chữ ký mới
              this.tempSignatureImage = ''; // Xóa preview tạm
              this.selectedSignatureMethod = ''; // Đóng form upload
              alert('Chữ ký đã được lưu thành công!');
            },
            error: (error) => {
              this.isUploadingSignature = false;
              console.error('Error uploading signature:', error);
              alert('Có lỗi xảy ra khi tải lên chữ ký: ' + (error.error?.message || error.message));
            }
          });
      });
  }

  // Hủy upload chữ ký
  cancelSignatureUpload(): void {
    this.tempSignatureImage = '';
    this.selectedSignatureMethod = '';
  }

  // Xóa chữ ký hiện tại
  clearSignature(): void {
    if (confirm('Bạn có chắc chắn muốn xóa chữ ký hiện tại?')) {
      // Gọi API để xóa chữ ký (nếu backend hỗ trợ)
      // Hoặc set signatureImage thành null/empty
      this.profile.signatureImage = '';
      alert('Chữ ký đã được xóa!');
    }
  }

  downloadSignature(): void {
    if (this.hasSignature() && this.profile.signatureImage) {
      const link = document.createElement('a');
      link.href = this.getFullImageUrl(this.profile.signatureImage);
      link.download = 'chu-ky.png';
      link.target = '_blank';
      link.click();
    } else {
      alert('Không có chữ ký để tải xuống!');
    }
  }

  // ========== FORM ACTIONS ==========
  saveChanges(): void {
    this.isSavingProfile = true;
    
    // Implement save logic here - gọi API update profile
    console.log('Saving changes:', this.formData);
    
    // Simulate API call
    setTimeout(() => {
      this.isSavingProfile = false;
      alert('Thông tin đã được cập nhật thành công!');
    }, 1000);
  }

  cancelChanges(): void {
    this.populateFormData();
    this.tempAvatarImage = '';
    this.tempSignatureImage = '';
    this.selectedSignatureMethod = '';
  }

  // Helper methods
  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'approved': return 'status-badge status-approved';
      case 'pending': return 'status-badge status-pending';
      case 'rejected': return 'status-badge status-rejected';
      default: return 'status-badge';
    }
  }

  getStatusText(status: string): string {
    switch (status) {
      case 'approved': return 'Đã duyệt';
      case 'pending': return 'Chờ duyệt';
      case 'rejected': return 'Từ chối';
      default: return status;
    }
  }

  getUserRole(): string {
    if (!this.profile.roles.length) return 'Nhân viên';
    return this.profile.roles[0].description || 'Nhân viên';
  }

  getUserDepartment(): string {
    return this.profile.department || 'Phòng Kinh doanh';
  }
}