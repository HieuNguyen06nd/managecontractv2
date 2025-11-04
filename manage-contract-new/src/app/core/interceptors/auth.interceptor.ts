import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
} from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  private readonly STATIC_EXT = /\.(png|jpe?g|gif|svg|ico|css|js|map|json|woff2?|ttf|eot)$/i;

  private isAbsolute(url: string): boolean {
    return /^https?:\/\//i.test(url);
  }

  private isStatic(url: string): boolean {
    return url.startsWith('/assets') || this.STATIC_EXT.test(url);
  }

  private isSameOrigin(url: string): boolean {
    try {
      const abs = this.isAbsolute(url) ? new URL(url) : new URL(url, window.location.origin);
      const current = window.location.origin;
      // nếu bạn có API riêng (khác origin), allow theo environment.apiBaseUrl
      const apiBase = (environment as any)?.apiBaseUrl;
      const apiOrigin = apiBase ? new URL(apiBase).origin : null;
      return abs.origin === current || (!!apiOrigin && abs.origin === apiOrigin);
    } catch {
      return true; // fallback: coi là same-origin với URL tương đối
    }
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (!isPlatformBrowser(this.platformId)) return next.handle(req);

    // bỏ qua asset tĩnh
    if (this.isStatic(req.url)) return next.handle(req);

    // nếu đã có Authorization, giữ nguyên
    if (req.headers.has('Authorization')) return next.handle(req);

    // chỉ gắn token cho same-origin (hoặc apiBaseUrl nếu cấu hình)
    if (!this.isSameOrigin(req.url)) return next.handle(req);

    const token = localStorage.getItem('token');
    if (!token) return next.handle(req);

    // đề phòng header quá lớn
    if (token.length > 4096) {
      console.warn('[AuthInterceptor] Token quá lớn, bỏ qua Authorization header.');
      return next.handle(req);
    }

    const authReq = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
    return next.handle(authReq);
  }
}
