export type EquipmentStatus = 'ACTIVE' | 'INACTIVE';

export interface Equipment {
  id: string;
  code: string;
  name: string;
  status: EquipmentStatus;
}

export interface EquipmentWriteRequest {
  code: string;
  name: string;
  status: EquipmentStatus;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  errors?: ApiFieldError[];
}
