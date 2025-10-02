import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { CommonModule } from '@angular/common';
import { AuthResponse, RoleResponse } from '../../core/models/auth.model';
import { Router } from '@angular/router';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss'],
})
export class HeaderComponent implements OnInit {
  userName = '';
  userRole = '';
  avatarUrl: string | null = null;
  isLoggedIn = false;

  constructor(private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.isLoggedIn = !!localStorage.getItem('token');
    if (this.isLoggedIn) {
      this.loadProfile();
    }
  }

  loadProfile() {
    this.authService.getProfile().subscribe({
      next: (res) => {
        const data = res.data;

        this.userName = data.fullName || 'Người dùng';
        this.userRole =
          data.roles && data.roles.length > 0
            ? data.roles[0].roleKey // hoặc lấy description
            : 'Người dùng';

        // nếu BE có ảnh chữ ký/avatar thì binding ở đây
        this.avatarUrl = data.signatureImage || null;
      },
      error: (err) => {
        console.error('Lỗi lấy profile:', err);
      },
    });
  }
logout() {
  const email = localStorage.getItem('email'); // lưu email khi login
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

}
