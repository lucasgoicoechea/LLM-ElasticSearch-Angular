import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { App } from './app';
import { EquipmentApiService } from './equipment-api.service';
import { ApiError, Equipment, EquipmentWriteRequest } from './equipment-api.types';

const activePump: Equipment = {
  id: 'd744b10a-e45c-4c93-b9bf-30c20c7c33e2',
  code: 'PUMP-01',
  name: 'Primary Pump',
  status: 'ACTIVE',
};
const inactiveValve: Equipment = {
  id: '9f2d3f22-8f76-4576-a8f5-0ab5f2529e8c',
  code: 'VALVE-01',
  name: 'Isolation Valve',
  status: 'INACTIVE',
};
const editedPump = { ...activePump, name: 'Primary Pump Updated', status: 'INACTIVE' as const };

class FakeEquipmentApiService {
  listResult: Observable<Equipment[]> = of([]);
  createResult: Observable<Equipment> = of(activePump);
  updateResult: Observable<Equipment> = of(editedPump);
  deleteResult: Observable<void> = of(undefined);
  listCalls = 0;
  createCalls: EquipmentWriteRequest[] = [];
  updateCalls: Array<{ id: string; equipment: EquipmentWriteRequest }> = [];
  deleteCalls: string[] = [];

  list(): Observable<Equipment[]> { this.listCalls += 1; return this.listResult; }
  create(equipment: EquipmentWriteRequest): Observable<Equipment> {
    this.createCalls.push(equipment); return this.createResult;
  }
  update(id: string, equipment: EquipmentWriteRequest): Observable<Equipment> {
    this.updateCalls.push({ id, equipment }); return this.updateResult;
  }
  delete(id: string): Observable<void> { this.deleteCalls.push(id); return this.deleteResult; }
}

describe('App equipment CRUD page', () => {
  let fixture: ComponentFixture<App>;
  let api: FakeEquipmentApiService;

  beforeEach(async () => {
    api = new FakeEquipmentApiService();
    api.listResult = of([activePump, inactiveValve]);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [{ provide: EquipmentApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
  });

  it('lists equipment with code, name, and status from the gateway service', () => {
    const rows = equipmentRows();
    expect(api.listCalls).toBe(1);
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('PUMP-01');
    expect(rows[0].textContent).toContain('Primary Pump');
    expect(rows[0].textContent).toContain('ACTIVE');
    expect(rows[1].textContent).toContain('VALVE-01');
    expect(rows[1].textContent).toContain('Isolation Valve');
    expect(rows[1].textContent).toContain('INACTIVE');
  });

  it('creates equipment and refreshes the list with the created item', () => {
    api.listResult = of([activePump, inactiveValve, editedPump]);
    fillForm('MOTOR-01', 'Mixer Motor', 'INACTIVE');
    submitForm();
    fixture.detectChanges();

    expect(api.createCalls).toEqual([{ code: 'MOTOR-01', name: 'Mixer Motor', status: 'INACTIVE' }]);
    expect(api.listCalls).toBe(2);
    expect(equipmentRows()).toHaveLength(3);
  });

  it('resets the form after successfully creating equipment', async () => {
    fillForm('MOTOR-01', 'Mixer Motor', 'INACTIVE');
    submitForm();
    await Promise.resolve();
    fixture.detectChanges();

    expect(formValues()).toEqual({ code: '', name: '', status: 'ACTIVE' });
    expect(fixture.componentInstance.editingId).toBeNull();
  });

  it('loads a selected item into the form and updates it', () => {
    clickButton('Edit PUMP-01');
    setInput('name', 'Primary Pump Updated');
    setSelect('status', 'INACTIVE');
    submitForm();
    fixture.detectChanges();

    expect(api.updateCalls).toEqual([{
      id: activePump.id,
      equipment: { code: 'PUMP-01', name: 'Primary Pump Updated', status: 'INACTIVE' },
    }]);
  });

  it('exits edit mode and resets the form after successfully updating equipment', async () => {
    clickButton('Edit PUMP-01');
    setInput('name', 'Primary Pump Updated');
    setSelect('status', 'INACTIVE');
    submitForm();
    await Promise.resolve();
    fixture.detectChanges();

    expect(api.updateCalls).toEqual([{
      id: activePump.id,
      equipment: { code: 'PUMP-01', name: 'Primary Pump Updated', status: 'INACTIVE' },
    }]);
    expect(fixture.componentInstance.editingId).toBeNull();
    expect(formValues()).toEqual({ code: '', name: '', status: 'ACTIVE' });
  });

  it('deletes an item and reloads the list without it', () => {
    api.listResult = of([inactiveValve]);
    clickButton('Delete PUMP-01');
    fixture.detectChanges();

    expect(api.deleteCalls).toEqual([activePump.id]);
    expect(api.listCalls).toBe(2);
    expect(pageText()).not.toContain('Primary Pump');
    expect(pageText()).toContain('Isolation Valve');
  });

  it('clears edit mode and resets the form when deleting the item being edited', async () => {
    api.listResult = of([inactiveValve]);
    clickButton('Edit PUMP-01');

    expect(formValues()).toEqual({ code: 'PUMP-01', name: 'Primary Pump', status: 'ACTIVE' });

    clickButton('Delete PUMP-01');
    await Promise.resolve();
    fixture.detectChanges();

    expect(api.deleteCalls).toEqual([activePump.id]);
    expect(fixture.componentInstance.editingId).toBeNull();
    expect(formValues()).toEqual({ code: '', name: '', status: 'ACTIVE' });
  });

  it('shows loading state while a request is pending and clears it after completion', async () => {
    const pendingCreate = new Subject<Equipment>();
    const pendingReload = new Subject<Equipment[]>();
    api.createResult = pendingCreate.asObservable();
    fillForm('MOTOR-01', 'Mixer Motor');
    submitForm();
    fixture.detectChanges();

    expect(pageText()).toContain('Loading equipment...');
    expect(button('Save equipment').disabled).toBe(true);

    api.listResult = pendingReload.asObservable();
    pendingCreate.next(activePump);
    pendingCreate.complete();
    fixture.detectChanges();
    expect(pageText()).toContain('Loading equipment...');

    pendingReload.next([activePump]);
    pendingReload.complete();
    await Promise.resolve();
    expect(fixture.componentInstance.isLoading).toBe(false);
  });

  it('shows normalized backend errors and recovers on the next successful reload', () => {
    const error: ApiError = {
      code: 'VALIDATION_ERROR',
      message: 'Invalid equipment payload',
      errors: [{ field: 'name', message: 'must not be blank' }],
    };
    api.createResult = throwError(() => error);
    fillForm('BAD-01', '');
    submitForm();
    fixture.detectChanges();

    expect(alertText()).toContain('VALIDATION_ERROR: Invalid equipment payload');
    expect(alertText()).toContain('name: must not be blank');

    api.createResult = of(activePump);
    api.listResult = of([activePump]);
    setInput('name', 'Recovered Pump');
    submitForm();
    fixture.detectChanges();

    expect(queryAlert()).toBeNull();
    expect(equipmentRows()).toHaveLength(1);
  });

  function equipmentRows(): HTMLTableRowElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
  }
  function fillForm(code: string, name: string, status = 'ACTIVE'): void {
    setInput('code', code); setInput('name', name); setSelect('status', status);
  }
  function setInput(name: string, value: string): void {
    const input = fixture.nativeElement.querySelector(`input[name="${name}"]`) as HTMLInputElement;
    input.value = value; input.dispatchEvent(new Event('input')); fixture.detectChanges();
  }
  function setSelect(name: string, value: string): void {
    const select = fixture.nativeElement.querySelector(`select[name="${name}"]`) as HTMLSelectElement;
    select.value = value; select.dispatchEvent(new Event('change')); fixture.detectChanges();
  }
  function submitForm(): void {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
  }
  function formValues(): EquipmentWriteRequest {
    return fixture.componentInstance.form;
  }
  function clickButton(label: string): void { button(label).click(); fixture.detectChanges(); }
  function button(label: string): HTMLButtonElement {
    const match = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button'))
      .find((candidate) => candidate.textContent?.trim() === label);
    if (!match) throw new Error(`Button not found: ${label}`);
    return match;
  }
  function queryAlert(): HTMLElement | null { return fixture.nativeElement.querySelector('[role="alert"]'); }
  function alertText(): string { return queryAlert()?.textContent ?? ''; }
  function pageText(): string { return fixture.nativeElement.textContent ?? ''; }
});
