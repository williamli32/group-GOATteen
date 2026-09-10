import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';


export interface LoginRequest {
  email: string;
  password: string;
}


export interface LoginResponse {
  accessToken: string;
}


@Injectable({
  providedIn: 'root'
})
export class Auth {

  private apiUrl = `${environment.apiUrl}/auth`;

  constructor(
    private http: HttpClient
  ) { }


  login(
    request: LoginRequest
  ): Observable<LoginResponse> {

    return this.http.post<LoginResponse>(
      `${this.apiUrl}/login`,
      request
    );

  }


  saveToken(token: string): void {
    localStorage.setItem(
      'accessToken',
      token
    );
  }


  getToken(): string | null {
    return localStorage.getItem(
      'accessToken'
    );
  }


  logout(): void {
    localStorage.removeItem(
      'accessToken'
    );
  }


  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  register(data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
  }) {
    return this.http.post(
      `${this.apiUrl}/register`,
      data
    );
  }

}