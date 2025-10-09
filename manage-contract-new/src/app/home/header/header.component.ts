import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { EmployeeService } from '../../core/services/employee.service'; // Thêm service này
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router'; // Thêm RouterModule

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule], // Thêm RouterModule
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {
  userName = '';
  userRole = '';
  avatarUrl: string | null = null;
  isLoggedIn = false;
  baseUrl: string = 'http://localhost:8080'; // URL base cho ảnh

  constructor(
    private authService: AuthService, 
    private employeeService: EmployeeService, // Thêm service
    private router: Router
  ) {}

  ngOnInit(): void {
    this.isLoggedIn = !!localStorage.getItem('token');
    if (this.isLoggedIn) {
      this.loadProfile();
    }
  }

  loadProfile() {
    // Sử dụng employeeService để lấy profile đầy đủ với avatar
    this.employeeService.getMyProfile().subscribe({
      next: (res) => {
        const data = res.data;
        console.log('Header profile:', data); // Debug

        this.userName = data.fullName || 'Người dùng';
        this.userRole = data.roles && data.roles.length > 0
          ? data.roles[0].description || data.roles[0].roleKey
          : 'Người dùng';

        // Xử lý avatar URL
        if (data.avatarImage) {
          this.avatarUrl = this.getFullImageUrl(data.avatarImage);
        } else {
          this.avatarUrl = null;
        }
      },
      error: (err) => {
        console.error('Lỗi lấy profile:', err);
      },
    });
  }

  // Xử lý URL ảnh (giống trong profile component)
  getFullImageUrl(relativePath: string | undefined | null): string {
    if (!relativePath) {
      return '';
    }
    
    if (relativePath.startsWith('http')) {
      return relativePath;
    }
    
    return `${this.baseUrl}/uploads/${relativePath}`;
  }

  logout() {
    const email = localStorage.getItem('email');
    this.authService.logout(email!).subscribe({
      next: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('email');
        this.router.navigate(['/auth/login']);
      },
      error: () => {
        // Dù lỗi thì vẫn clear token ở FE
        localStorage.removeItem('token');
        localStorage.removeItem('email');
        this.router.navigate(['/auth/login']);
      }
    });
  }

  // Điều hướng đến trang profile
  goToProfile() {
    this.router.navigate(['/profile']);
  }
}