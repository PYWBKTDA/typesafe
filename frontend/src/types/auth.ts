export type RegisterRequest = { username: string; password: string }
export type LoginRequest = { username: string; password: string }
export type AuthResponse = { token?: string; user?: { id: number; username: string } }
