import { Injectable } from '@angular/core';
import { CreateUserRequest, UpdateUserRequest, User } from '../models/users.model';
import { BaseCrudService } from '../../../core/data/base-crud.service';

@Injectable({ providedIn: 'root' })
export class UsersService extends BaseCrudService<User, CreateUserRequest, UpdateUserRequest> {
  protected readonly basePath = '/api/users';
}
