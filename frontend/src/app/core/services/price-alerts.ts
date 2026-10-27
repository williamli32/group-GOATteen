import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Subject, tap } from 'rxjs';

export type AlertDirection = 'ABOVE' | 'BELOW';
export type NoticeClearedEvent = { kind: 'one'; id: number } | { kind: 'all' };
export interface PriceAlert {
  id:number; instrumentId:number; symbol:string; currency:string;
  direction:AlertDirection; threshold:number; enabled:boolean; armed:boolean;
  triggeredAt:string|null; generation:number;
}
export interface PriceNotice {
  id:number; alertId:number; instrumentId:number; symbol:string;
  direction:AlertDirection; threshold:number; observedPrice:number;
  currency:string; quoteAt:string; createdAt:string; readAt:string|null;
}
export interface PriceAlertChange {
  instrumentId:number; direction:AlertDirection; threshold:number; enabled:boolean;
}
@Injectable({providedIn:'root'})
export class PriceAlertsService {
  private readonly http=inject(HttpClient);
  private readonly url=environment.apiUrl+'/price-alerts';
  private readonly cleared = new Subject<NoticeClearedEvent>();
  readonly noticeCleared$ = this.cleared.asObservable();
  list(){return this.http.get<PriceAlert[]>(this.url);}
  create(body:PriceAlertChange){return this.http.post<PriceAlert>(this.url,body);}
  update(id:number,body:PriceAlertChange){return this.http.put<PriceAlert>(`${this.url}/${id}`,body);}
  enabled(id:number,enabled:boolean){return this.http.patch<PriceAlert>(`${this.url}/${id}/enabled`,{enabled});}
  delete(id:number){return this.http.delete<void>(`${this.url}/${id}`);}
  notifications(){return this.http.get<PriceNotice[]>(`${this.url}/notifications`);}
  // Legacy mark-read endpoint is retained for older clients.
  read(id:number){return this.http.patch<void>(`${this.url}/notifications/${id}/read`,{});}
  clearNotice(id:number){
    return this.http.delete<void>(`${this.url}/notifications/${id}`).pipe(
      tap(() => this.cleared.next({kind:'one',id}))
    );
  }
  clearAllNotices(){
    return this.http.delete<void>(`${this.url}/notifications`).pipe(
      tap(() => this.cleared.next({kind:'all'}))
    );
  }
}
