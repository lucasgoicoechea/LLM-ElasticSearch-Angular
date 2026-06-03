import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { EquipmentApiService } from './equipment-api.service';
import {
  ApiError,
  Equipment,
  EquipmentWriteRequest,
} from './equipment-api.types';

describe('EquipmentApiService', () => {
  let service: EquipmentApiService;
  let http: HttpTestingController;

  const equipment: Equipment = {
    id: 'd744b10a-e45c-4c93-b9bf-30c20c7c33e2',
    code: 'PUMP-01',
    name: 'Primary Pump',
    status: 'ACTIVE',
  };

  const writeRequest: EquipmentWriteRequest = {
    code: 'PUMP-01',
    name: 'Primary Pump',
    status: 'ACTIVE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(EquipmentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('resolves from the root injector without a manual service provider', () => {
    expect(TestBed.inject(EquipmentApiService)).toBeInstanceOf(
      EquipmentApiService,
    );
  });

  it('lists equipment through the gateway collection endpoint', async () => {
    const result = firstValueFrom(service.list());
    const request = http.expectOne('/api/equipment');

    expect(request.request.method).toBe('GET');
    request.flush([equipment]);
    await expect(result).resolves.toEqual([equipment]);
  });

  it('gets equipment through the gateway item endpoint', async () => {
    const result = firstValueFrom(service.get(equipment.id));
    const request = http.expectOne(`/api/equipment/${equipment.id}`);

    expect(request.request.method).toBe('GET');
    request.flush(equipment);
    await expect(result).resolves.toEqual(equipment);
  });

  it('creates equipment through the gateway collection endpoint', async () => {
    const result = firstValueFrom(service.create(writeRequest));
    const request = http.expectOne('/api/equipment');

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(writeRequest);
    request.flush(equipment);
    await expect(result).resolves.toEqual(equipment);
  });

  it('updates equipment through the gateway item endpoint', async () => {
    const updated = { ...equipment, name: 'Backup Pump' };
    const updateRequest = { ...writeRequest, name: 'Backup Pump' };
    const result = firstValueFrom(service.update(equipment.id, updateRequest));
    const request = http.expectOne(`/api/equipment/${equipment.id}`);

    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(updateRequest);
    request.flush(updated);
    await expect(result).resolves.toEqual(updated);
  });

  it('deletes equipment through the gateway item endpoint', async () => {
    const result = firstValueFrom(service.delete(equipment.id));
    const request = http.expectOne(`/api/equipment/${equipment.id}`);

    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    await expect(result).resolves.toBeNull();
  });

  it('exposes normalized gateway errors unchanged', async () => {
    const normalizedError: ApiError = {
      code: 'VALIDATION_ERROR',
      message: 'Invalid equipment payload',
      errors: [{ field: 'name', message: 'must not be blank' }],
    };
    const result = firstValueFrom(service.create(writeRequest));
    const request = http.expectOne('/api/equipment');

    request.flush(normalizedError, {
      status: 400,
      statusText: 'Bad Request',
    });

    await expect(result).rejects.toEqual(normalizedError);
  });

  it('normalizes non-contract HTTP failures', async () => {
    const result = firstValueFrom(service.list());
    const request = http.expectOne('/api/equipment');

    request.flush('proxy offline', {
      status: 502,
      statusText: 'Bad Gateway',
    });

    await expect(result).rejects.toEqual({
      code: 'HTTP_ERROR',
      message: 'Equipment request failed with status 502.',
    });
  });
});
