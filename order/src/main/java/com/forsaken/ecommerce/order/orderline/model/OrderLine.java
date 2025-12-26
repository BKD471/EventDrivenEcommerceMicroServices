package com.forsaken.ecommerce.order.orderline.model;

import com.forsaken.ecommerce.order.order.model.Order;
import com.forsaken.ecommerce.order.orderline.dto.OrderLineResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "customer_order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_order_line_seq")
    @SequenceGenerator(
            name = "customer_order_line_seq",
            sequenceName = "customer_order_line_seq",
            allocationSize = 1
    )
    private Integer id;

    /**
     * Owning side of the relationship.
     * Order controls lifecycle via cascade + orphanRemoval.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Integer productId;

    @Column(nullable = false)
    private double quantity;

    public void setOrder(final Order order) {
        this.order = order;
    }

    public OrderLineResponse toOrderLineResponse() {
        return OrderLineResponse.builder()
                .id(this.getId())
                .quantity(this.getQuantity())
                .build();
    }
}
