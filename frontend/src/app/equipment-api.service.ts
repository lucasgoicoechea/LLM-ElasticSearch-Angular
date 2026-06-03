import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, Observable, throwError } from 'rxjs';

import {
  ApiError,
  Equipment,
  EquipmentWriteRequest,
} from './equipment-api.types';

const EQUIPMENT_API_URL = '/api/equipment';

@Injectable({ providedIn: 'root' })
export class EquipmentApiService {
  private readonly http = inject(HttpClient);

  list(): Observable<Equipment[]> {
    return this.request(this.http.get<Equipment[]>(EQUIPMENT_API_URL));
  }

  get(id: string): Observable<Equipment> {
    return this.request(this.http.get<Equipment>(this.itemUrl(id)));
  }

  create(equipment: EquipmentWriteRequest): Observable<Equipment> {
    return this.request(
      this.http.post<Equipment>(EQUIPMENT_API_URL, equipment),
    );
  }

  update(id: string, equipment: EquipmentWriteRequest): Observable<Equipment> {
    return this.request(
      this.http.put<Equipment>(this.itemUrl(id), equipment),
    );
  }

  delete(id: string): Observable<void> {
    return this.request(this.http.delete<void>(this.itemUrl(id)));
  }

  private itemUrl(id: string): string {
    return `${EQUIPMENT_API_URL}/${encodeURIComponent(id)}`;
  }

  private request<T>(request: Observable<T>): Observable<T> {
    return request.pipe(
      catchError((error: HttpErrorResponse) =>
        throwError(() => this.normalizeError(error)),
      ),
    );
  }

  private normalizeError(error: HttpErrorResponse): ApiError {
    if (isApiError(error.error)) {
      return error.error;
    }

    return {
      code: 'HTTP_ERROR',
      message: `Equipment request failed with status ${error.status}.`,
    };
  }
}

function isApiError(error: unknown): error is ApiError {
  if (typeof error !== 'object' || error === null) {
    return false;
  }

  const candidate = error as Partial<ApiError>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string';
}
