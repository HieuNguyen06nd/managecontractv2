import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export interface AuthClaims {
  sub?: string;
  userId?: number;
  roles?: string[];
  authorities?: string[]; // permissionKey
  exp?: number;
  [k: string]: any;
}

@Injectable({ providedIn: 'root' })
export class AuthState {
  private _token$   = new BehaviorSubject<string | null>(localStorage.getItem('token'));
  private _refresh$ = new BehaviorSubject<string | null>(localStorage.getItem('refreshToken'));
  private _claims$  = new BehaviorSubject<AuthClaims | null>(null);

  token$ = this._token$.asObservable();
  claims$ = this._claims$.asObservable();

  get token()   { return this._token$.value; }
  get refresh() { return this._refresh$.value; }
  get claims()  { return this._claims$.value; }

  setSession(token: string, refresh: string | null, claims: AuthClaims | null) {
    this._token$.next(token);
    if (refresh) this._refresh$.next(refresh);
    this._claims$.next(claims || undefined as any);

    localStorage.setItem('token', token);
    if (refresh) localStorage.setItem('refreshToken', refresh);
    if (claims?.userId) localStorage.setItem('userId', String(claims.userId));
    if (claims?.roles) localStorage.setItem('roles', JSON.stringify(claims.roles));
  }

  clear() {
    this._token$.next(null);
    this._refresh$.next(null);
    this._claims$.next(null);
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('roles');
  }

  hasRole(role: string) {
    const roles = this.claims?.roles || JSON.parse(localStorage.getItem('roles') || '[]');
    return Array.isArray(roles) && roles.includes(role);
  }

  hasPerm(perm: string) {
    const perms = this.claims?.authorities || [];
    return Array.isArray(perms) && perms.includes(perm);
  }

  hasAnyPerm(perms: string[]) {
    return perms.some(p => this.hasPerm(p));
  }

  isExpired(): boolean {
    const exp = this.claims?.exp;
    if (!exp) return false;
    return Date.now() / 1000 > exp - 30; // chừa 30s
  }
}
