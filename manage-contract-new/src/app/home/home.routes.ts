import { Routes } from '@angular/router';
import { HomeLayoutComponent } from './home-layout/home-layout.component';
import { DasboadComponent } from './dasboad/dasboad.component';
import { ContractTemplateComponent } from './contract-template/contract-template.component';
import { PositionComponent } from './position/position.component';
import { DepartmentComponent } from './department/department.component';
import { ContactSignComponent } from './contact-sign/contact-sign.component';
import { UserComponent } from './user/user.component';
import { AuthGuard } from '../core/guards/auth.guard';
import { PermissionGuard } from '../core/guards/permission.guard';
import { ContactListTemplateComponent } from './contact-list-template/contact-list-template.component';
import { ContractComponent } from './contract/contract.component';
import { ContractFlowComponent } from './contract-flow/contract-flow.component';
import { RolePermissionComponent } from './role-permission/role-permission.component';
import { ProfileComponent } from './profile/profile.component';
import { ContractListComponent } from './contract-list/contract-list.component';
import { DeparmentPositionComponent } from './deparment-position/deparment-position.component';

export const HOME_ROUTES: Routes = [
  {
    path: '',
    component: HomeLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      { path: '', component: DasboadComponent },

      // Danh sách hợp đồng
      {
        path: 'contract/list',
        component: ContractListComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: [
            'CONTRACT_VIEW_ALL', 'CONTRACT_VIEW_DEPT', 'CONTRACT_VIEW_OWN',
            'contract.view.all', 'contract.view.dept', 'contract.view.own'
          ],
          match: 'any'
        }
      },

      // Mẫu hợp đồng
      {
        path: 'contract/templates/create',
        component: ContractTemplateComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['TEMPLATE_VIEW', 'template.view'],
          match: 'any'
        }
      },
      {
        path: 'contract/templates/list',
        component: ContactListTemplateComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['TEMPLATE_VIEW', 'template.view'],
          match: 'any'
        }
      },

      // Quy trình
      {
        path: 'contract/flow',
        component: ContractFlowComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['FLOW_VIEW', 'FLOW_CREATE', 'FLOW_UPDATE', 'flow.view', 'flow.manage'],
          match: 'any'
        }
      },

      // Tạo hợp đồng
      {
        path: 'contract/create',
        component: ContractComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['CONTRACT_CREATE', 'contract.create'],
          match: 'any'
        }
      },

      // Ký / phê duyệt
      {
        path: 'contract/sign',
        component: ContactSignComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['APPROVAL_STEP_SIGN', 'APPROVAL_STEP_APPROVE', 'approval.step.sign', 'approval.step.approve'],
          match: 'any'
        }
      },

      // Vị trí / Phòng ban
      {
        path: 'contract/positions',
        component: PositionComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['POSITION_MANAGE', 'position.manage'],
          match: 'any'
        }
      },
      {
        path: 'contract/departments',
        component: DepartmentComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['DEPARTMENT_MANAGE', 'department.manage'],
          match: 'any'
        }
      },

      // Người dùng
      {
        path: 'contract/users',
        component: UserComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['USER_MANAGE', 'user.manage'],
          match: 'any'
        }
      },

      // Vai trò & phân quyền (nếu chưa seed ROLE_* thì để ADMIN là đủ)
      {
        path: 'roles-perms',
        component: RolePermissionComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          match: 'any'
        }
      },

      // Hồ sơ
      { path: 'profile', component: ProfileComponent },

      // Gộp màn hình phòng ban & vị trí
      {
        path: 'department-position',
        component: DeparmentPositionComponent,
        canActivate: [PermissionGuard],
        data: {
          roles: ['ADMIN'],
          permissions: ['DEPARTMENT_MANAGE', 'POSITION_MANAGE', 'department.manage', 'position.manage'],
          match: 'any'
        }
      },

      { path: '**', redirectTo: '' }
    ]
  }
];
