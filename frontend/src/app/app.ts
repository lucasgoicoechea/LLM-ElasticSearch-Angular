import { Component, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { EquipmentApiService } from './equipment-api.service';
import { ApiError, Equipment, EquipmentStatus, EquipmentWriteRequest } from './equipment-api.types';

const EMPTY_FORM: EquipmentWriteRequest = {
  code: '',
  name: '',
  status: 'ACTIVE',
};

@Component({
  selector: 'app-root',
  imports: [FormsModule],
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly equipmentApi = inject(EquipmentApiService);
  private pendingRequests = 0;

  equipment: Equipment[] = [];
  form: EquipmentWriteRequest = { ...EMPTY_FORM };
  editingId: string | null = null;
  error: ApiError | null = null;
  isLoading = false;
  statuses: EquipmentStatus[] = ['ACTIVE', 'INACTIVE'];

  ngOnInit(): void {
    this.loadEquipment();
  }

  loadEquipment(): void {
    this.startRequest();
    this.equipmentApi.list()
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: (equipment) => {
          this.equipment = equipment;
          this.error = null;
        },
        error: (error: ApiError) => this.showError(error),
      });
  }

  saveEquipment(): void {
    const request = this.editingId
      ? this.equipmentApi.update(this.editingId, this.form)
      : this.equipmentApi.create(this.form);

    this.error = null;
    this.startRequest();
    request.pipe(finalize(() => this.finishRequest())).subscribe({
      next: () => {
        queueMicrotask(() => this.resetForm());
        this.loadEquipment();
      },
      error: (error: ApiError) => this.showError(error),
    });
  }

  editEquipment(equipment: Equipment): void {
    this.editingId = equipment.id;
    this.form = {
      code: equipment.code,
      name: equipment.name,
      status: equipment.status,
    };
    this.error = null;
  }

  deleteEquipment(equipment: Equipment): void {
    this.error = null;
    this.startRequest();
    this.equipmentApi.delete(equipment.id)
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: () => {
          if (this.editingId === equipment.id) {
            queueMicrotask(() => this.resetForm());
          }
          this.loadEquipment();
        },
        error: (error: ApiError) => this.showError(error),
      });
  }

  resetForm(): void {
    this.editingId = null;
    this.form = { ...EMPTY_FORM };
  }

  private startRequest(): void {
    this.pendingRequests += 1;
    this.isLoading = true;
  }

  private finishRequest(): void {
    queueMicrotask(() => {
      this.pendingRequests = Math.max(0, this.pendingRequests - 1);
      this.isLoading = this.pendingRequests > 0;
    });
  }

  private showError(error: ApiError): void {
    this.error = error;
  }
}


