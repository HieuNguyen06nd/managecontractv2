import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { EmployeeService } from '../../core/services/employee.service';
import { AuthProfileResponse } from '../../core/models/auth.model';
import { ResponseData } from '../../core/models/response-data.model';
import { BehaviorSubject, debounceTime, distinctUntilChanged, of, Subject } from 'rxjs';
import { DepartmentService } from '../../core/services/department.service';
import {  DepartmentResponse } from '../../core/models/department.model'

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule,FormsModule, ReactiveFormsModule],
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss'],
})
export class UserComponent implements OnInit {
  users: AuthProfileResponse[] = [];
  filteredUsers: AuthProfileResponse[] = [];
  roles: string[] = ['Quản trị viên', 'Quản lý', 'Người dùng']; // Mock data
  departments: DepartmentResponse[] = [];
  
  isAddModalOpen = false;
  isEditModalOpen = false;
  isDeleteModalOpen = false;
  isResetModalOpen = false;
  
  currentUserId: number | null = null;
  currentUserName: string = '';
  
  addForm: FormGroup;
  editForm: FormGroup;
  
  searchTerms = new BehaviorSubject<string>('');  // để dùng được .value
  filterStatusTerm: string = '';  // bỏ private
  filterRoleTerm: string = '';    // bỏ private

  constructor(private fb: FormBuilder, private employeeService: EmployeeService, private departmentService: DepartmentService) {
    this.addForm = this.fb.group({
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      role: ['', Validators.required],
      departmentId: [null],     
    });
    
    this.editForm = this.fb.group({
      id: [null],
      fullName: ['', Validators.required],
      email: ['', [Validators.required, Validators.email]],
      phone: [''],
      role: ['', Validators.required],
      departmentId: [null],     
      status: [''],
    });
  }

  ngOnInit(): void {
    this.loadUsers();
    
    this.searchTerms.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(term => this.filterUsers());
  }
  
  loadUsers(): void {
    this.employeeService.getAll().subscribe(res => {
      this.users = res.data;
      this.filterUsers();
    });
  }
  
  onSearch(event: Event): void {
    const term = (event.target as HTMLInputElement).value;
    this.searchTerms.next(term);
  }
  
  onFilterChange(): void {
    this.filterUsers();
  }
  
 filterUsers(): void {
    let tempUsers = this.users;

    // Lọc theo tên, email, sđt
    if (this.searchTerms.value) {
      const searchTerm = this.searchTerms.value.toLowerCase();
      tempUsers = tempUsers.filter(user =>
        user.fullName.toLowerCase().includes(searchTerm) ||
        user.email.toLowerCase().includes(searchTerm) ||
        (user.phone && user.phone.includes(searchTerm))
      );
    }

    // Lọc theo trạng thái
    if (this.filterStatusTerm) {
      tempUsers = tempUsers.filter(user =>
        user.status === this.filterStatusTerm
      );
    }

    // Lọc theo vai trò
    if (this.filterRoleTerm) {
      tempUsers = tempUsers.filter(user =>
        user.roles.some(role => role.description === this.filterRoleTerm)
      );
    }

    this.filteredUsers = tempUsers;
  }

  openAddUserModal(): void {
    this.addForm.reset();
    this.isAddModalOpen = true;
  }
  
  openEditUserModal(user: AuthProfileResponse): void {
    this.currentUserId = user.id;
    this.editForm.patchValue({
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      phone: user.phone,
      role: user.roles.length > 0 ? user.roles[0].description : '',
      department: user.department || '',   
      status: user.status,
    });
    this.isEditModalOpen = true;
  }
  
  openDeleteUserModal(userId: number, fullName: string): void {
    this.currentUserId = userId;
    this.currentUserName = fullName;
    this.isDeleteModalOpen = true;
  }
  
  openResetPasswordModal(userId: number, fullName: string): void {
    this.currentUserId = userId;
    this.currentUserName = fullName;
    this.isResetModalOpen = true;
  }
  
  closeModal(): void {
    this.isAddModalOpen = false;
    this.isEditModalOpen = false;
    this.isDeleteModalOpen = false;
    this.isResetModalOpen = false;
  }

  addUser(): void {
    if (this.addForm.valid) {
      this.employeeService.create(this.addForm.value).subscribe(res => {
        this.loadUsers();
        this.closeModal();
        alert('Đã thêm người dùng thành công!'); // Thay bằng Toastr
      });
    }
  }

  updateUser(): void {
    if (this.editForm.valid && this.currentUserId !== null) {
      this.employeeService.update(this.currentUserId, this.editForm.value).subscribe(res => {
        this.loadUsers();
        this.closeModal();
        alert('Đã cập nhật người dùng thành công!');
      });
    }
  }

  deleteUser(): void {
    if (this.currentUserId !== null) {
      this.employeeService.delete(this.currentUserId).subscribe(res => {
        this.loadUsers();
        this.closeModal();
        alert('Đã xóa người dùng thành công!');
      });
    }
  }

  // Phương thức reset mật khẩu sẽ được thêm vào service
  resetPassword(): void {
    if (this.currentUserId !== null) {
      // Gọi service.resetPassword(this.currentUserId).subscribe(...)
      this.closeModal();
      alert('Đã reset mật khẩu thành công!');
    }
  }
}