export type AuthSession = {
  token: string;
  userId: number;
  email: string;
  role: 'SELLER' | 'BUYER';
  isSeller: boolean;
};

export type Item = {
  id: string;
  userId: number;
  name: string;
  description: string;
  price: number;
  currency: string;
  stock: number;
};

export type ItemInput = {
  name: string;
  description: string;
  price: number;
  currency: string;
  stock: number;
};

export type Order = {
  id: number;
  userId: number;
  totalPrice: number;
  currency: string;
  status: string;
  createdAt: string;
  items: Array<{
    itemId: string;
    itemName: string;
    quantity: number;
    price: number;
  }>;
};
