export interface Response<T> {
  code: number;
  message: string;
  data: T;
  errors?: string[];
}

export type ApiResponse<T> = Response<T>;

export type Nullable<T> = T | null;

export type Id = string | number;

export type BaseDTO = Record<string, unknown>;

export interface PaginationParams {
  page?: number;
  size?: number;
  sort?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
