import { Injectable } from '@angular/core';
import {
  CanActivate,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  Router,
} from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class PermissionGuard implements CanActivate {
  constructor(private router: Router) {}

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): boolean {
    const rolesStr = localStorage.getItem('roles');
    if (!rolesStr) {
      this.router.navigate(['/auth/login']);
      return false;
    }

    const roles = JSON.parse(rolesStr);

    // Lấy role yêu cầu từ route data
    const requiredRole = route.data['role'];

    // Kiểm tra user có role này không
    const hasRole = roles.some((r: any) => r.roleKey === requiredRole);

    if (!hasRole) {
      this.router.navigate(['/unauthorized']);
      return false;
    }

    return true;
  }
}
