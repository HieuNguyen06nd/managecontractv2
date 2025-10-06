import { Injectable } from '@angular/core';
import {
  CanActivate,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  Router,
} from '@angular/router';

@Injectable({ providedIn: 'root' })
export class PermissionGuard implements CanActivate {
  constructor(private router: Router) {}

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const rolesStr = localStorage.getItem('roles');
    if (!rolesStr) {
      this.router.navigate(['/auth/login']);
      return false;
    }

    const userRoles: any[] = JSON.parse(rolesStr) || [];
    const requiredRole: string | undefined = route.data['role'];
    const requiredRoles: string[] | undefined = route.data['roles'];

    let allowed = true;
    if (requiredRole) {
      allowed = userRoles.some(r => (r.roleKey || r.role_key) === requiredRole);
    } else if (requiredRoles?.length) {
      allowed = userRoles.some(r => requiredRoles.includes(r.roleKey || r.role_key));
    }

    if (!allowed) {
      this.router.navigate(['/unauthorized']);
      return false;
    }
    return true;
  }
}
