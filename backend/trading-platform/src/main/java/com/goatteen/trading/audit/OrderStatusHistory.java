package com.goatteen.trading.audit;

import com.goatteen.trading.order.Order;
import com.goatteen.trading.order.OrderStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;


    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;


    @Column
    private String note;


    public Long getId() {
        return id;
    }


    public Order getOrder() {
        return order;
    }


    public OrderStatus getStatus() {
        return status;
    }


    public LocalDateTime getChangedAt() {
        return changedAt;
    }


    public String getNote() {
        return note;
    }


    public void setOrder(Order order) {
        this.order = order;
    }


    public void setStatus(OrderStatus status) {
        this.status = status;
    }


    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }


    public void setNote(String note) {
        this.note = note;
    }
}
