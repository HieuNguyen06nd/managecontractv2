export interface Category {
  id: number;
  code: string;
  name: string;
  description?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface CategoryResponse {
  code: number;
  message: string;
  data: Category[];
}