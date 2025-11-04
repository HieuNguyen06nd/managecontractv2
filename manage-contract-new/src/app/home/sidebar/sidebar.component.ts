import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

type MenuItem = {
  key: string;
  label: string;
  icon?: string;
  link?: string;
  exact?: boolean;
  children?: MenuItem[];
  roles?: string[];        // any-of
  permissions?: string[];  // any-of (chấp nhận UPPER_SNAKE_CASE & lower.dot.case)
};

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.scss'],
})
export class SidebarComponent {
  private openGroups = new Set<string>();

  // ===== Chuẩn hoá =====
  private toUpperSnake = (k: string) =>
    String(k || '').trim().replaceAll('.', '_').replaceAll('-', '_').toUpperCase();
  private toLowerDot = (k: string) =>
    String(k || '').trim().replaceAll('_', '.').replaceAll('-', '.').toLowerCase();

  private roleSet = new Set<string>(
    ((JSON.parse(localStorage.getItem('roles') || '[]') as any[]) || [])
      .map((r) => String((r?.roleKey ?? r?.role_key ?? r) || '').toUpperCase())
      .filter(Boolean)
  );

  // Lấy permission từ localStorage (string hoặc object {permissionKey})
  private permUpper = new Set<string>(
    ((JSON.parse(localStorage.getItem('permissions') || '[]') as any[]) || [])
      .map((p) => {
        const raw = typeof p === 'string'
          ? p
          : (p?.permissionKey ?? p?.permission_key ?? p?.key ?? p?.code ?? '');
        return this.toUpperSnake(raw);
      })
      .filter(Boolean)
  );
  private permLower = new Set<string>(Array.from(this.permUpper).map((k) => this.toLowerDot(k)));

  private hasPerm = (req: string): boolean => {
    const up = this.toUpperSnake(req);
    const low = this.toLowerDot(req);
    return this.permUpper.has(up) || this.permLower.has(low);
  };

  /** Cấu hình menu: đã dùng đúng key seed */
  items: MenuItem[] = [
    { key: 'home', label: 'Trang chủ', icon: 'fas fa-home', link: '/', exact: true },

    // Phê duyệt / ký
    {
      key: 'approve-sign',
      label: 'Phê duyệt / Ký duyệt',
      icon: 'fas fa-clipboard-check',
      children: [
        {
          key: 'approve',
          label: 'Phê duyệt / Ký nháy',
          link: '/contract/sign',
          icon: 'fas fa-check-circle',
          permissions: [
            'APPROVAL_STEP_SIGN', 'APPROVAL_STEP_APPROVE',
            'approval.step.sign', 'approval.step.approve'
          ],
        },
      ],
    },

    // Quản lý hợp đồng
    {
      key: 'contract-mgmt',
      label: 'Quản lý hợp đồng',
      icon: 'fas fa-file-alt',
      children: [
        {
          key: 'contracts',
          label: 'Danh sách hợp đồng',
          link: '/contract/list',
          icon: 'fas fa-list-ul',
          permissions: [
            'CONTRACT_VIEW_ALL', 'CONTRACT_VIEW_DEPT', 'CONTRACT_VIEW_OWN',
            'contract.view.all', 'contract.view.dept', 'contract.view.own'
          ],
        },
        {
          key: 'contract-create',
          label: 'Tạo mới hợp đồng',
          link: '/contract/create',
          icon: 'fas fa-plus-square',
          permissions: ['CONTRACT_CREATE', 'contract.create'],
        },
      ],
    },

    // Mẫu hợp đồng
    {
      key: 'templates',
      label: 'Mẫu hợp đồng',
      icon: 'fas fa-clone',
      children: [
        {
          key: 'tpl-list',
          label: 'Danh sách mẫu',
          link: '/contract/templates',
          icon: 'fas fa-th-list',
          permissions: ['TEMPLATE_VIEW', 'template.view'],
        },
      ],
    },

    // Quy trình
    {
      key: 'flow',
      label: 'Quy trình',
      icon: 'fas fa-project-diagram',
      children: [
        {
          key: 'flow-manage',
          label: 'Quy trình duyệt',
          link: '/contract/flow',
          icon: 'fas fa-stream',
          permissions: ['FLOW_VIEW', 'FLOW_CREATE', 'FLOW_UPDATE', 'flow.view', 'flow.manage'],
        },
      ],
    },

    // Tổ chức
    {
      key: 'org',
      label: 'Tổ chức',
      icon: 'fas fa-sitemap',
      children: [
        {
          key: 'dept-pos',
          label: 'Phòng ban & Vị trí',
          link: '/department-position',
          icon: 'fas fa-building',
          permissions: ['DEPARTMENT_MANAGE', 'POSITION_MANAGE', 'department.manage', 'position.manage'],
        },
        {
          key: 'users',
          label: 'Người dùng',
          link: '/contract/users',
          icon: 'fas fa-users',
          permissions: ['USER_MANAGE', 'user.manage'],
          roles: ['ADMIN'],
        },
        {
          key: 'roles-perms',
          label: 'Vai trò & Quyền',
          link: '/contract/roles-perms',
          icon: 'fas fa-user-shield',
          permissions: ['ROLE_MANAGE', 'PERMISSION_MANAGE', 'role.manage', 'permission.manage'],
          roles: ['ADMIN'],
        },
      ],
    },

    // Cài đặt / Đăng xuất
    { key: 'settings', label: 'Cài đặt', icon: 'fas fa-cog', link: '/settings', roles: ['ADMIN'] },
    { key: 'logout', label: 'Đăng xuất', icon: 'fas fa-sign-out-alt', link: '__logout__' },
  ];

  constructor(private router: Router) {}

  ngOnInit(): void {
    const url = this.router.url || '';
    this.items.forEach((g) => {
      if (g.children?.some((c) => c.link && url.startsWith(c.link!))) {
        this.openGroups.add(g.key);
      }
    });
  }

  visible(it: MenuItem): boolean {
    const needRoles = (it.roles || []).map((r) => String(r || '').toUpperCase());
    const needPerms = (it.permissions || []);

    const okRole = !needRoles.length || needRoles.some((r) => this.roleSet.has(r));
    const okPerm = !needPerms.length || needPerms.some((p) => this.hasPerm(p));

    return okRole && okPerm;
  }

  toggleGroup(it: MenuItem) {
    if (this.openGroups.has(it.key)) this.openGroups.delete(it.key);
    else this.openGroups.add(it.key);
  }
  isOpen(it: MenuItem) { return this.openGroups.has(it.key); }

  trackByKey = (_: number, it: MenuItem) => it.key;

  onClickLeaf(it: MenuItem) {
    if (it.link === '__logout__') {
      localStorage.clear();
      this.router.navigateByUrl('/auth/login');
    }
  }
}
