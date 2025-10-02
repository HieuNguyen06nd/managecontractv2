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

export const HOME_ROUTES: Routes = [
  {
    path: '',
    component: HomeLayoutComponent,
    canActivate: [AuthGuard], // tất cả route con yêu cầu login
    children: [
      { path: '', component: DasboadComponent },

      { 
        path: 'contract/templates',
        component: ContractTemplateComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' } // chỉ ADMIN mới vào
      },
      { 
        path: 'contract/flow',
        component: ContractFlowComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' } // chỉ MANAGER
      },
      { 
        path: 'contract/create',
        component: ContractComponent,
        canActivate: [PermissionGuard],
      data: { role: 'ADMIN' } 
      },
      { 
        path: 'contract/sign',
        component: ContactSignComponent,
        canActivate: [PermissionGuard],
         data: { role: 'ADMIN' } 
      },
      { 
        path: 'contract/positions',
        component: PositionComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' }
      },
      { 
        path: 'contract/departments',
        component: DepartmentComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' }
      },
      { 
        path: 'contract/users',
        component: UserComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' } 
      },

      { 
        path: 'contract/templates/list',
        component: ContactListTemplateComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' }
      },
      { 
        path: 'role',
        component: RolePermissionComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' }
      },
      { 
        path: 'profile',
        component: ProfileComponent,
        canActivate: [PermissionGuard],
        data: { role: 'ADMIN' }
      },

      { path: '**', redirectTo: '' }
    ]
  }
];
