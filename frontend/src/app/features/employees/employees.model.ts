export interface Employee {
  id: number;
  userId: number;
  name: string;
  email: string;
  phone: string | null;
  active: boolean;
  assignedCares: CareRef[];
  createdAt: string;
}

export interface CareRef {
  id: number;
  name: string;
}

export interface CreateEmployeeRequest {
  name: string;
  email: string;
  phone?: string;
  password: string;
  careIds: number[];
}

export interface UpdateEmployeeRequest {
  name?: string;
  phone?: string;
  active?: boolean;
  careIds?: number[];
}
