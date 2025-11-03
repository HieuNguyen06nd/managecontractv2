import { Injectable } from '@angular/core';
import {
  CanActivate,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  Router,
  UrlTree,
} from '@angular/router';
import { ToastrService } from 'ngx-toastr';

@Injectable({ providedIn: 'root' })
export class PermissionGuard implements CanActivate {
  constructor(private router: Router, private toastr: ToastrService) {}

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    const token = localStorage.getItem('token');
    if (!token) {
      this.toastr.info('Vui lòng đăng nhập để tiếp tục');
      return this.router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }

    const payload = this.safeDecodeJwt<any>(token);
    if (!payload || this.isExpired(payload?.exp)) {
      // xoá session nhẹ nhàng
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      this.toastr.info('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      return this.router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }

    // 1) Lấy roles/permissions đã chuẩn hoá sẵn khi login
    let roles: Array<{ roleKey: string }> =
      this.safeParse<Array<{ roleKey: string }>>(localStorage.getItem('roles')) || [];

    let permissions: string[] =
      this.safeParse<string[]>(localStorage.getItem('permissions')) || [];

    // 2) Nếu chưa có, rút từ token
    if (!roles.length && Array.isArray(payload?.roles)) {
      roles = payload.roles
        .map((r: any) => ({ roleKey: r.roleKey || r.role_key || r.key || r.name }))
        .filter((r: any) => !!r.roleKey);
    }
    if (!permissions.length) {
      if (Array.isArray(payload?.permissions)) {
        // support permissions ở top-level
        permissions = payload.permissions.map((p: any) =>
          (p?.permissionKey || p?.key || p)?.toString().toLowerCase()
        ).filter(Boolean);
      } else if (Array.isArray(payload?.roles)) {
        // lấy từ roles[].permissions[]
        permissions = this.flattenPermissionsFromRoles(payload.roles);
      } else if (Array.isArray(payload?.authorities)) {
        // loại ROLE_
        permissions = payload.authorities
          .filter((a: string) => typeof a === 'string' && !a.startsWith('ROLE_'))
          .map((a: string) => a.toLowerCase());
      }
    }

    // Chuẩn hoá so sánh
    const roleSet = new Set(roles.map(r => String(r.roleKey || '').toUpperCase()));
    const permSet = new Set(permissions.map(p => String(p).toLowerCase()));

    // Yêu cầu từ route.data
    const requiredRole: string | undefined = route.data['role'];
    const requiredRoles: string[] | undefined = route.data['roles'];
    const requiredPermission: string | undefined = route.data['permission'];
    const requiredPermissions: string[] | undefined = route.data['permissions'];
    const match: 'any' | 'all' = route.data['match'] || 'any';

    let allowed = true;

    // Role check
    if (requiredRole) {
      allowed = roleSet.has(requiredRole.toUpperCase());
    } else if (requiredRoles?.length) {
      const req = requiredRoles.map(r => r.toUpperCase());
      allowed = match === 'all' ? req.every(r => roleSet.has(r)) : req.some(r => roleSet.has(r));
    }

    // Permission check
    if (allowed && (requiredPermission || (requiredPermissions?.length))) {
      if (requiredPermission) {
        allowed = permSet.has(requiredPermission.toLowerCase());
      } else if (requiredPermissions?.length) {
        const req = requiredPermissions.map(p => p.toLowerCase());
        allowed = match === 'all' ? req.every(p => permSet.has(p)) : req.some(p => permSet.has(p));
      }
    }

    if (!allowed) {
      const msg = requiredPermissions?.length || requiredPermission
        ? `Bạn không có quyền truy cập. Cần quyền: ${
            requiredPermission || requiredPermissions?.join(', ')
          }`
        : `Bạn không có quyền truy cập. Cần vai trò: ${
            requiredRole || requiredRoles?.join(', ')
          }`;
      this.toastr.warning(msg, 'Không có quyền');

      return this.router.createUrlTree(['/unauthorized'], {
        queryParams: {
          from: state.url,
          needRole: requiredRole || (requiredRoles ? requiredRoles.join(',') : undefined),
          needPerm: requiredPermission || (requiredPermissions ? requiredPermissions.join(',') : undefined),
        }
      });
    }

    return true;
  }

  // ===== helpers =====
  private safeDecodeJwt<T = any>(jwt: string | undefined): T | null {
    if (!jwt) return null;
    const parts = jwt.split('.');
    if (parts.length !== 3) return null;
    try {
      const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        .padEnd(parts[1].length + (4 - (parts[1].length % 4)) % 4, '=');
      const json = atob(b64);
      return JSON.parse(json) as T;
    } catch {
      return null;
    }
  }

  private isExpired(exp?: number): boolean {
    if (!exp) return false;
    const nowSec = Math.floor(Date.now() / 1000);
    return exp <= nowSec;
  }

  private safeParse<T>(raw: string | null): T | null {
    if (!raw) return null;
    try { return JSON.parse(raw) as T; } catch { return null; }
  }

  private flattenPermissionsFromRoles(roles: any[]): string[] {
    const set = new Set<string>();
    roles.forEach((r: any) => {
      if (Array.isArray(r.permissions)) {
        r.permissions.forEach((p: any) => {
          const key = (p?.permissionKey || p?.key || p)?.toString().toLowerCase();
          if (key) set.add(key);
        });
      }
    });
    return Array.from(set);
  }
}
