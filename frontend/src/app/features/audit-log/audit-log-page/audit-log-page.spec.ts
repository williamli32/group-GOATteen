import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AuditLogPage } from './audit-log-page';

describe('AuditLogPage', () => {
  let component: AuditLogPage;
  let fixture: ComponentFixture<AuditLogPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogPage],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditLogPage);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
