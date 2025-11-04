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

  // không dùng replaceAll để tránh lỗi target ES thấp
  private toUpperSnake(k: string) {
    return String(k || '').trim().replace(/[.\-]/g, '_').toUpperCase();
  }
  private toLowerDot(k: string) {
    return String(k || '').trim().replace(/[_\-]/g, '.').toLowerCase();
  }

  private safeDecodeJwt<T = any>(jwt: string | undefined): T | null {
    if (!jwt) return null;
    const parts = jwt.split('.');
    if (parts.length !== 3) return null;
    try {
      const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        .padEnd(parts[1].length + (4 - (parts[1].length % 4)) % 4, '=');
      return JSON.parse(atob(b64)) as T;
    } catch { return null; }
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

  private buildPermSets(src: any[]): { upper: Set<string>; lower: Set<string> } {
    const upper = new Set<string>();
    src.forEach((p) => {
      const raw = typeof p === 'string'
        ? p
        : (p?.permissionKey ?? p?.permission_key ?? p?.key ?? p?.code ?? '');
      const up = this.toUpperSnake(raw);
      if (up) upper.add(up);
    });
    const lower = new Set<string>(Array.from(upper).map(u => this.toLowerDot(u)));
    return { upper, lower };
  }

  private hasPerm(req: string, upper: Set<string>, lower: Set<string>): boolean {
    const up = this.toUpperSnake(req);
    const low = this.toLowerDot(req);
    return upper.has(up) || lower.has(low);
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | UrlTree {
    // 0) đăng nhập
    const token = localStorage.getItem('token');
    if (!token) {
      this.toastr.info('Vui lòng đăng nhập để tiếp tục');
      return this.router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }
    const payload = this.safeDecodeJwt<any>(token);
    if (!payload || this.isExpired(payload?.exp)) {
      localStorage.removeItem('token');
      localStorage.removeItem('refreshToken');
      this.toastr.info('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
      return this.router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
    }

    // 1) lấy roles/permissions
    let roles: Array<{ roleKey: string }> =
      this.safeParse<Array<{ roleKey: string }>>(localStorage.getItem('roles')) || [];

    let rawPerms: any[] =
      this.safeParse<any[]>(localStorage.getItem('permissions')) || [];

    if (!roles.length && Array.isArray(payload?.roles)) {
      roles = payload.roles
        .map((r: any) => ({ roleKey: r?.roleKey ?? r?.role_key ?? r?.key ?? r?.name }))
        .filter((r: any) => !!r.roleKey);
    }

    if (!rawPerms.length) {
      if (Array.isArray(payload?.permissions)) {
        rawPerms = payload.permissions;
      } else if (Array.isArray(payload?.roles)) {
        const set = new Set<any>();
        payload.roles.forEach((r: any) => (r?.permissions || []).forEach((p: any) => set.add(p)));
        rawPerms = Array.from(set);
      } else if (Array.isArray(payload?.authorities)) {
        rawPerms = payload.authorities
          .filter((a: string) => typeof a === 'string' && !a.startsWith('ROLE_'));
      }
    }

    const roleSet = new Set<string>();
    roles.forEach((r) => {
      const k = String(r.roleKey || '').toUpperCase();
      if (k) {
        roleSet.add(k); // ADMIN
        if (!k.startsWith('ROLE_')) roleSet.add('ROLE_' + k); // ROLE_ADMIN
      }
    });

    const { upper: permUpper, lower: permLower } = this.buildPermSets(rawPerms);

    // 2) ADMIN bypass
    if (roleSet.has('ADMIN') || roleSet.has('ROLE_ADMIN')) return true;

    // 3) route data
    const requiredRole: string | undefined = route.data?.['role'];
    const requiredRoles: string[] | undefined = route.data?.['roles'];
    const requiredPermission: string | undefined = route.data?.['permission'];
    const requiredPermissions: string[] | undefined = route.data?.['permissions'];

    const matchStr: any = route.data?.['match'];
    const match: 'any' | 'all' =
      typeof matchStr === 'string' ? (matchStr.toLowerCase() as any) : 'any';

    const needsRole = !!requiredRole || !!(requiredRoles?.length);
    const needsPerm = !!requiredPermission || !!(requiredPermissions?.length);

    let okRole = true;
    if (needsRole) {
      const reqRoles = (requiredRole ? [requiredRole] : requiredRoles!)
        .map(r => String(r).toUpperCase());
      okRole = match === 'all'
        ? reqRoles.every(r => roleSet.has(r) || roleSet.has('ROLE_' + r))
        : reqRoles.some(r => roleSet.has(r) || roleSet.has('ROLE_' + r));
    }

    let okPerm = true;
    if (needsPerm) {
      const reqPerms = (requiredPermission ? [requiredPermission] : requiredPermissions!) as string[];
      okPerm = match === 'all'
        ? reqPerms.every(p => this.hasPerm(p, permUpper, permLower))
        : reqPerms.some(p => this.hasPerm(p, permUpper, permLower));
    }

    const allowed = match === 'all' ? (okRole && okPerm) : (okRole || okPerm);

    if (!allowed) {
      const needPermMsg = requiredPermission || requiredPermissions?.join(', ');
      const needRoleMsg = requiredRole || requiredRoles?.join(', ');
      this.toastr.warning(
        needsPerm
          ? `Bạn không có quyền truy cập. Cần quyền: ${needPermMsg}`
          : `Bạn không có quyền truy cập. Cần vai trò: ${needRoleMsg}`,
        'Không có quyền'
      );

      return this.router.createUrlTree(['/unauthorized'], {
        queryParams: { from: state.url, needRole: needRoleMsg, needPerm: needPermMsg },
      });
    }

    return true;
  }
}
