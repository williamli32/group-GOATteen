import {
    Injectable,
    signal,
    effect
} from '@angular/core';


export type NotificationType =
    'success' | 'error' | 'info' | 'warning';


export interface Notification {
    id: string;
    message: string;
    type: NotificationType;
    duration?: number; // milliseconds, default 5000
}


@Injectable({
    providedIn: 'root'
})
export class NotificationService {

    notifications = signal<Notification[]>([]);

    private notificationId = 0;

    private timers = new Map<string, number>();


    constructor() {
        // Auto-dismiss notifications based on duration
        effect(() => {
            const current = this.notifications();
            current.forEach(notification => {
                if (!this.timers.has(notification.id)) {
                    const duration = notification.duration ?? 5000;
                    const timerId = window.setTimeout(
                        () => this.dismiss(notification.id),
                        duration
                    );
                    this.timers.set(notification.id, timerId);
                }
            });
        });
    }


    show(
        message: string,
        type: NotificationType = 'info',
        duration: number = 5000
    ): string {
        const id = `notification-${++this.notificationId}`;

        const notification: Notification = {
            id,
            message,
            type,
            duration
        };

        this.notifications.update(
            current => [
                notification,
                ...current
            ]
        );

        return id;
    }


    dismiss(id: string): void {
        // Clear any pending timer
        const timerId = this.timers.get(id);
        if (timerId !== undefined) {
            window.clearTimeout(timerId);
            this.timers.delete(id);
        }

        this.notifications.update(
            current => current.filter(
                n => n.id !== id
            )
        );
    }


    dismissAll(): void {
        this.timers.forEach(timerId => {
            window.clearTimeout(timerId);
        });
        this.timers.clear();
        this.notifications.set([]);
    }

}
