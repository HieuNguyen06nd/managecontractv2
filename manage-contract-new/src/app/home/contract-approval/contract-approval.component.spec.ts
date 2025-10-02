import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ContractApprovalComponent } from './contract-approval.component';

describe('ContractApprovalComponent', () => {
  let component: ContractApprovalComponent;
  let fixture: ComponentFixture<ContractApprovalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ContractApprovalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ContractApprovalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
